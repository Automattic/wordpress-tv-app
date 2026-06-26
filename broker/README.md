# WordPress TV — Pairing Broker

A small, stateful **OAuth rendezvous service** that lets an unauthenticated TV
(tvOS / Google TV) get a WordPress.com access token for the private **a8c.tv**
source — without the user ever typing a password on the TV.

Why it exists, the full flow, and every locked decision are in
[`../spec/a8c-tv-qr-login.html`](../spec/a8c-tv-qr-login.html). This README is just
how to run it.

```
TV ──POST /session──▶ Broker ──QR──▶ Phone (Safari) ──passkey──▶ WordPress.com
TV ◀──poll /session{id}── Broker ◀──code→token── Phone ◀──302──── WordPress.com
```

The TV shows a QR, the phone logs in (passkeys work in mobile Safari), the broker
runs the **Authorization Code** exchange server-side and buffers the token; the TV
polls and collects it **once**.

## Endpoints

| Endpoint            | Auth          | Does |
|---------------------|---------------|------|
| `POST /session`     | none          | Mint `session_id` + `poll_secret`; return `{ session_id, poll_secret, qr_url, ttl }`. |
| `GET /pair/{id}`    | none          | 302 → WP.com `/oauth2/authorize` with `state={id}`. |
| `GET /callback`     | none          | Validate `state`; exchange `code`→token (uses `client_secret`); show a "return to your TV" page. |
| `GET /session/{id}` | `poll_secret` | `{status:pending}`, or `{status:authorized, access_token}` **once** then deletes the record. Expired → `410`. |
| `GET /healthz`      | none          | Liveness check. |

`poll_secret` goes in the `X-Poll-Secret` header (or `?poll_secret=`). It binds the
poll to the TV that created the session, so only that TV can collect the token.

## What you need to provide

The broker is the easy part — it's done. The real flow isn't purely local, so it
needs two things from you before it can work end to end:

### 1. A WordPress.com OAuth application

Register one at <https://developer.wordpress.com/apps/>. It gives you a
**`client_id`** and **`client_secret`**, and you set its **Redirect URL**.

- **Type:** Web (server-side; we hold a `client_secret` and use Authorization Code).
- **Redirect URL:** must be **exactly** `https://<your-broker-domain>/callback`
  — WP.com matches it character-for-character.
- Don't reuse another app's `client_id` (e.g. not Pocket Casts' `85091`).

### 2. A public HTTPS URL for the broker

WP.com redirects the phone's browser back to your `/callback`, so the broker must
be reachable from the public internet over **HTTPS** at a **stable** domain.

- **Production:** Fly / Render / Cloud Run hands you an HTTPS domain — use that.
- **Local testing:** tunnel to `localhost:8080` and use the tunnel's HTTPS URL:
  ```sh
  cloudflared tunnel --url http://localhost:8080   # or: ngrok http 8080
  ```
  ⚠️ Quick tunnels (cloudflared `--url`, free ngrok) hand out a **new subdomain each
  run**, and the Redirect URL must match the app registration exactly — so either
  re-register on each run, or use a **named cloudflared tunnel / reserved ngrok
  domain** for a stable URL.

### 3. The `.env` values

```sh
cp .env.example .env
```

| Var                   | You set it to |
|-----------------------|---------------|
| `WPCOM_CLIENT_ID`     | from the WP.com app |
| `WPCOM_CLIENT_SECRET` | from the WP.com app |
| `PUBLIC_BASE_URL`     | your HTTPS domain, no trailing slash (e.g. the tunnel URL) |
| `REDIRECT_URI`        | leave blank → defaults to `$PUBLIC_BASE_URL/callback` (must match the app) |

That's it. No database, no Redis, no secrets beyond the WP.com client credentials.

> **Also required (not config):** the person who scans + logs in must have read
> access to the private a8c.tv site. And per spec §9, whether a `WPCOM_SCOPE` is
> needed to read a private site + mint the VideoPress playback JWT is still
> **open to verify** — start with it empty.

## Run it

```sh
# 1. (local) start a tunnel → note the https URL
cloudflared tunnel --url http://localhost:8080

# 2. register/point the WP.com app's Redirect URL at  https://<that-url>/callback
# 3. put client_id / client_secret / PUBLIC_BASE_URL into .env
# 4. start the broker
docker compose up --build
```

Then exercise it (the TV's calls + a real phone login):

```sh
# TV creates a session
curl -s -X POST https://<your-url>/session | jq
# → { session_id, poll_secret, qr_url, ttl }

# Open qr_url on your phone (this is what the QR encodes), log into WP.com, approve.

# TV polls until authorized (needs the poll secret)
curl -s "https://<your-url>/session/<session_id>" -H "X-Poll-Secret: <poll_secret>" | jq
# → {"status":"pending"} … then {"status":"authorized","access_token":"…"}
```

## Deploy (Fly.io)

`fly.toml` is configured for **one always-on machine** — the session store is
in-memory and single-instance, so it must never scale to zero or run more than one.
Secrets come from a gitignored `fly.secrets` file (not the shell):

```sh
cp fly.secrets.example fly.secrets   # fill in WPCOM_CLIENT_ID / WPCOM_CLIENT_SECRET
make deploy                          # = fly secrets import < fly.secrets && fly deploy
```

`make secrets` re-pushes them on their own when they change. Non-secret config
(`PUBLIC_BASE_URL`, `PORT`) lives in `fly.toml [env]`.

One-time app creation: `fly launch --no-deploy --copy-config --name <app> --region <r>`
(run from this `broker/` dir so Fly finds the Dockerfile + `fly.toml`). Then register
`https://<app>.fly.dev/callback` as a redirect URL on the WP.com app.

## Configuration

| Env var               | Required | Default | Notes |
|-----------------------|:--------:|---------|-------|
| `WPCOM_CLIENT_ID`     | ✅       | —       | From developer.wordpress.com/apps. |
| `WPCOM_CLIENT_SECRET` | ✅       | —       | Server-side only; never logged. |
| `PUBLIC_BASE_URL`     |          | `http://localhost:8080` | Public base for `qr_url`; HTTPS in prod. |
| `REDIRECT_URI`        |          | `$PUBLIC_BASE_URL/callback` | Must match the WP.com app exactly. |
| `WPCOM_AUTHORIZE_URL` |          | `…/oauth2/authorize` | Override only for testing against a mock. |
| `WPCOM_TOKEN_URL`     |          | `…/oauth2/token` | Override only for testing against a mock. |
| `WPCOM_SCOPE`         |          | _(empty)_ | See spec §9 open questions. |
| `PORT`                |          | `8080`  | |

## Develop without Docker

```sh
./gradlew test            # unit + route tests
./gradlew run             # needs WPCOM_CLIENT_ID / WPCOM_CLIENT_SECRET in the env
```

## Security notes (from spec §8)

- `session_id` / `poll_secret` are 256-bit, CSPRNG, URL-safe.
- Sessions are short-lived (5 min) and **single-use** (deleted on collect).
- The token is **a8c.tv-scoped** — minimal blast radius if leaked.
- The `client_secret` lives only in the broker env; tokens are never logged.
- Terminate **HTTPS** in front of this container in production.
- Not yet implemented (intentionally, for this scaffold): rate-limiting on
  `/session`, `/session/{id}`, `/callback`. Add before public deploy.

## Stack

Kotlin + [Ktor](https://ktor.io) (Netty), in-memory session store, no database.
Single long-running container — local Docker now, Fly/Render/Cloud Run later.
