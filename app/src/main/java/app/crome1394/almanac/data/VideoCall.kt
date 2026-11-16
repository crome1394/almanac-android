package app.crome1394.almanac.data

/** A video-call link found in an event, shown as "Join with …" the way Google shows Meet. */
data class VideoCall(val service: String, val url: String) {
    companion object {
        private val url = Regex("""https://[^\s<>"')\]]+""", RegexOption.IGNORE_CASE)
        private val services = listOf(
            "Google Meet" to Regex("""^meet\.google\.com$"""),
            "Zoom" to Regex("""^([a-z0-9-]+\.)?zoom\.(us|com)$"""),
            "Microsoft Teams" to Regex("""^teams\.(microsoft|live)\.com$"""),
            "Jitsi Meet" to Regex("""^(meet\.jit\.si|8x8\.vc)$"""),
            "Webex" to Regex("""^([a-z0-9-]+\.)?webex\.com$"""),
            "Whereby" to Regex("""^whereby\.com$"""),
            "FaceTime" to Regex("""^facetime\.apple\.com$"""),
            "Signal" to Regex("""^signal\.link$"""),
            "Nextcloud Talk" to Regex("""^.+/call/[a-z0-9]+$"""),
        )

        /** The first video-call link in [texts] (location first, then description), if any. Only https links count. */
        fun find(vararg texts: String): VideoCall? {
            for (t in texts) {
                for (m in url.findAll(t.take(20_000))) {
                    val link = m.value.trimEnd('.', ',', ';', ':', '!', '?')
                    val rest = link.substringAfter("://")
                    val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':').lowercase()
                    val hostAndPath = rest.substringBefore('?').substringBefore('#').lowercase()
                    services.firstOrNull { (name, r) -> if (name == "Nextcloud Talk") r.matches(hostAndPath) else r.matches(host) }
                        ?.let { return VideoCall(it.first, link) }
                }
            }
            return null
        }
    }
}
