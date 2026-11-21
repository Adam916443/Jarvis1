package app.shilex.android.tools

import app.shilex.android.study.StudyLock
import org.json.JSONObject

/** Student lock by voice when Gemini heard the request (offline commands handle the plain English one). */
object StudyTools {
    fun all(): List<PhoneTool> = listOf(StudyLockTool)

    private object StudyLockTool : PhoneTool {
        override val name = "student_lock"

        override fun declaration() = Schema.function(
            name,
            "Turns on student lock (study mode, focus mode, 'قفل المذاكرة'): the assistant then stays completely silent until the user says " +
                "Hey Jarvis and then talk. Use when the user asks for student lock, study mode or to stay quiet while they study.",
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val line = StudyLock.start(host.context)
            host.endSessionSoon()
            return Results.done("Student lock is on. Say this in the user's language in one very short sentence, then stop talking: \"$line\"")
        }
    }
}
