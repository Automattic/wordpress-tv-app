package com.automattic.wordpresstv.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Drives the pairing screen: creates a broker session, surfaces the `qr_url` to
 * render, and polls until the broker hands back a token (or the session fails).
 *
 * [run] is one long-lived suspend call that owns the whole flow, so an expired QR
 * just loops back and regenerates itself without UI flicker. The screen runs it
 * inside a `LaunchedEffect`; cancellation (screen dismissed) stops the loop.
 * Mirrors the Apple `PairingViewModel`.
 */
class PairingViewModel(private val broker: BrokerClient) {

    enum class Failure { BrokerUnreachable, OAuthDenied, TokenExchange, Unknown }

    sealed interface State {
        data object Creating : State
        data class Showing(val qrUrl: String) : State
        data class Success(val result: BrokerClient.PairingResult) : State
        data class Failed(val failure: Failure) : State
    }

    var state by mutableStateOf<State>(State.Creating)
        private set

    suspend fun run() {
        // Outer loop lets an expired session transparently regenerate.
        while (coroutineContext.isActive) {
            state = State.Creating
            val session = try {
                broker.createSession()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state = State.Failed(Failure.BrokerUnreachable)
                return
            }
            state = State.Showing(session.qrUrl)
            if (pollUntilDone(session)) return // terminal (success/failure)
            // Otherwise the session expired → loop and mint a fresh one.
        }
    }

    /** Returns true when terminal (handled here), false when expired (reissue). */
    private suspend fun pollUntilDone(session: BrokerClient.Session): Boolean {
        while (coroutineContext.isActive) {
            delay(POLL_INTERVAL_MS)
            val result = try {
                broker.poll(session.id, session.pollSecret)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue // transient network blip — keep polling
            }
            when (result) {
                BrokerClient.PollResult.Pending -> continue
                is BrokerClient.PollResult.Authorized -> {
                    state = State.Success(result.result)
                    return true
                }
                is BrokerClient.PollResult.Failed -> {
                    state = State.Failed(failureFor(result.reason))
                    return true
                }
                BrokerClient.PollResult.Expired -> return false
            }
        }
        return true
    }

    private fun failureFor(reason: String): Failure = when (reason) {
        "oauth_denied" -> Failure.OAuthDenied
        "token_exchange_failed" -> Failure.TokenExchange
        else -> Failure.Unknown
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
