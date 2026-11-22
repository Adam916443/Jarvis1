package app.shilex.android.tools

import android.content.Intent
import app.shilex.android.vision.VisionActivity
import org.json.JSONObject

/** "Look at this": opens the Vision screen, which streams the camera to the live session. */
object VisionTools {
    fun all(): List<PhoneTool> = listOf(CameraLook)

    private object CameraLook : PhoneTool {
        override val name = "camera_look"

        override fun declaration() = Schema.function(
            name,
            "Turns the phone's camera on so you can see what the user points it at. Use when they say look at this, what is this, " +
                "what am I holding, read this, identify this, or ask you to see something (also \"شوف ده\", \"إيه ده\"). " +
                "After it opens you receive a picture about once a second.",
        )

        override suspend fun run(args: JSONObject, host: ToolHost): JSONObject {
            val intent = Intent(host.context, VisionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return host.openApp(
                intent,
                "Camera",
                "The camera opens after you speak. Say in a few words that you're looking, then describe what you see in one or two short sentences.",
            )
        }
    }
}
