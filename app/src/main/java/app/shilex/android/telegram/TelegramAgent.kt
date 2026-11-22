package app.shilex.android.telegram

import android.content.Context
import app.shilex.android.Diag
import app.shilex.android.assistant.ToolRunner
import app.shilex.android.data.KeyVault
import app.shilex.android.live.FunctionCall
import app.shilex.android.live.FunctionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Answers one Telegram text with Gemini (the user's own key, plain text API) and runs the
 * harmless phone tools it asks for ([ToolRunner] in remote mode). Keeps a short chat history.
 */
class TelegramAgent(private val context: Context, private val http: OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val history = mutableListOf<JSONObject>()
    private var lastAt = 0L

    fun clear() {
        history.clear()
    }

    suspend fun reply(text: String): String {
        val key = KeyVault(context).read(KeyVault.GEMINI_KEY) ?: return "Add your Gemini key in the JARVIS app first."
        val now = System.currentTimeMillis()
        if (now - lastAt > IDLE_MS) history.clear()
        lastAt = now
        history += turn("user", JSONObject().put("text", text))
        trim()
        return try {
            talk(key)
        } catch (e: Exception) {
            Diag.w("telegram: agent failed (${e.javaClass.simpleName})")
            "Sorry, that didn't work. Try again in a moment."
        }
    }

    private suspend fun talk(key: String): String {
        val declarations = ToolRunner.remoteDeclarations(context, http, scope)
        repeat(MAX_ROUNDS) {
            val content = generate(key, declarations)
            history += content
            val parts = content.optJSONArray("parts") ?: JSONArray()
            val calls = (0 until parts.length()).mapNotNull { i -> parts.getJSONObject(i).optJSONObject("functionCall") }
            if (calls.isEmpty()) {
                val answer = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
                return answer.ifEmpty { "Done." }
            }
            val results = runTools(calls.mapIndexed { i, c -> FunctionCall("t$i", c.optString("name"), c.optJSONObject("args") ?: JSONObject()) })
            val responses = JSONArray()
            results.forEach { responses.put(JSONObject().put("functionResponse", JSONObject().put("name", it.name).put("response", it.response))) }
            history += JSONObject().put("role", "user").put("parts", responses)
        }
        return "That took too many steps. Try asking in a simpler way."
    }

    private suspend fun runTools(calls: List<FunctionCall>): List<FunctionResult> {
        val done = CompletableDeferred<List<FunctionResult>>()
        val runner = ToolRunner.remote(context, http, scope) { done.complete(it) }
        runner.userSpoke()
        runner.handle(calls)
        return try {
            withTimeout(TOOLS_TIMEOUT_MS) { done.await() }
        } catch (_: TimeoutCancellationException) {
            calls.map { FunctionResult(it.id, it.name, JSONObject().put("status", "failed").put("message", "That took too long.")) }
        }
    }

    private suspend fun generate(key: String, declarations: JSONArray): JSONObject {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system()))))
            .put("contents", JSONArray(history))
            .put("generationConfig", JSONObject().put("temperature", 0.4))
        if (declarations.length() > 0) body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations)))
        val request = Request.Builder()
            .url("$ENDPOINT/$MODEL:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val text = http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("gemini http ${response.code}")
            response.body?.string().orEmpty()
        }
        val candidate = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)
        // The whole content is kept as received (it may carry thought signatures the next call needs).
        return candidate?.optJSONObject("content")?.put("role", "model") ?: throw java.io.IOException("empty answer")
    }

    private fun system() = "You are JARVIS, the user's phone assistant, answering in a Telegram chat. Reply in the user's language, in one to three short " +
        "plain sentences, no markdown. Use the tools to act on the phone. You cannot open apps, call or send messages from here: say they must do that on the " +
        "phone. Never invent results; if a tool fails, say so. Now: " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm z")) + "."

    private fun turn(role: String, part: JSONObject) = JSONObject().put("role", role).put("parts", JSONArray().put(part))

    /** Keeps the last turns, starting at a plain user message so tool calls and answers stay paired. */
    private fun trim() {
        while (history.size > MAX_TURNS) history.removeAt(0)
        while (history.isNotEmpty() && !startsWithText(history[0])) history.removeAt(0)
    }

    private fun startsWithText(turn: JSONObject): Boolean =
        turn.optString("role") == "user" && turn.optJSONArray("parts")?.optJSONObject(0)?.has("text") == true

    private companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MODEL = "gemini-flash-latest"
        const val MAX_ROUNDS = 6
        const val MAX_TURNS = 24
        const val IDLE_MS = 30 * 60_000L
        const val TOOLS_TIMEOUT_MS = 40_000L
    }
}
