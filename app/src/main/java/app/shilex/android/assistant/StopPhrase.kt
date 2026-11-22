package app.shilex.android.assistant

/**
 * "Jarvis stop": once the name and filler words are taken out, nothing but "stop" (or bye) may
 * be left, so "stop the music" is not one. The name is spelled many ways by the transcriber.
 * Pure, so it is unit-tested.
 */
object StopPhrase {
    private val FILLER = setOf(
        "jarvis", "jarvice", "jarves", "harvis", "travis", "service", "hey", "hi", "ok", "okay", "please", "plz", "it", "now",
        "listening", "talking", "the", "conversation", "chat", "session", "yes", "yeah",
        "جارفس", "جارفيس", "جاروس", "جارفز", "يا", "من", "فضلك", "الان", "دلوقتي",
    )
    private val STOPS = setOf("stop", "bye", "goodbye", "enough", "وقف", "ستوب", "اقفل", "كفاية", "كفايه", "خلاص")

    fun matches(text: String): Boolean {
        val words = text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(" ").filter { it.isNotBlank() && it !in FILLER }
        return words.size == 1 && words[0] in STOPS
    }
}
