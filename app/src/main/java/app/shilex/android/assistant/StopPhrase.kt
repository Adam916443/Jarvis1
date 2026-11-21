package app.shilex.android.assistant

/**
 * "Jarvis stop": the whole sentence must be a stop request, so "stop the music" is not one.
 * Pure, so it is unit-tested.
 */
object StopPhrase {
    private val PHRASES = setOf(
        "stop", "stop it", "stop jarvis", "jarvis stop", "hey jarvis stop", "ok jarvis stop", "okay jarvis stop",
        "jarvis stop it", "jarvis please stop", "please stop", "stop please", "stop listening", "jarvis stop listening",
        "bye", "goodbye", "bye jarvis", "goodbye jarvis", "jarvis bye", "jarvis goodbye",
        "جارفس وقف", "جارفيس وقف", "وقف", "وقف يا جارفس", "جارفس ستوب", "ستوب", "جارفس اقفل", "اقفل",
    )

    fun matches(text: String): Boolean {
        val s = text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
        return s in PHRASES
    }
}
