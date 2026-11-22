package app.shilex.android.tools

import org.json.JSONObject

/** Lets the model end the conversation itself when the user says stop, enough or bye (in any language). */
object SessionTools {
    fun all(): List<PhoneTool> = listOf(EndConversation)

    private object EndConversation : PhoneTool {
        override val name = "end_conversation"

        override fun declaration() = Schema.function(
            name,
            "Ends the conversation. Call it as soon as the user says stop, Jarvis stop, enough, that's all, bye, or tells you to be quiet " +
                "(also in Arabic: وقف، كفاية، خلاص). Say nothing, or at most one word like Okay.",
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            host.endSessionSoon()
            return Results.done("Ending the conversation now. Say nothing more.")
        }
    }
}
