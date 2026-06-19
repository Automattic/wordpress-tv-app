package tv.a8c.broker

private const val CARD_STYLE = """
  body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
    font:17px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
    background:#f5f6f8;color:#1e1e1e}
  .card{background:#fff;border:1px solid #e0e2e7;border-radius:16px;padding:40px 36px;max-width:420px;
    text-align:center;box-shadow:0 8px 30px rgba(0,0,0,.06)}
  .icon{font-size:48px;line-height:1;margin-bottom:10px}
  h1{font-size:22px;margin:6px 0}
  p{margin:8px 0 0;color:#646970}
"""

/** The "return to your TV" page shown to the phone after the OAuth dance. */
fun donePage(message: String, ok: Boolean): String {
    val icon = if (ok) "✅" else "⚠️"
    val title = if (ok) "All set" else "Something went wrong"
    return """
        <!doctype html>
        <html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>WordPress TV · Pairing</title>
        <style>$CARD_STYLE</style></head>
        <body><div class="card">
          <div class="icon">$icon</div>
          <h1>$title</h1>
          <p>$message</p>
        </div></body></html>
    """.trimIndent()
}
