package app.shilex.android.telegram

import android.content.Context
import app.shilex.android.Diag
import app.shilex.android.data.KeyVault
import app.shilex.android.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * Control JARVIS from Telegram. The user makes a bot with @BotFather and pastes its token here
 * (kept in the Keystore vault); messages from the one paired chat are answered by Gemini
 * ([TelegramAgent]) and may run a short list of harmless phone tools. Anyone else is ignored.
 * The loop needs the process alive, so VoiceService stays in the foreground while it is on.
 */
object TelegramBot {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _status = MutableStateFlow("Off")
    val status: StateFlow<String> = _status.asStateFlow()

    /** Shown in Settings while pairing; the chat that sends "/pair <code>" becomes the owner. */
    @Volatile var pairingCode: String? = null
        private set

    fun wanted(context: Context): Boolean = Settings(context).telegramOn && KeyVault(context).read(KeyVault.TELEGRAM_TOKEN) != null

    fun newPairingCode(): String = Random.nextInt(100_000, 1_000_000).toString().also { pairingCode = it }

    /** Starts or stops the polling loop to match the settings. */
    @Synchronized
    fun sync(context: Context) {
        val app = context.applicationContext
        if (wanted(app)) {
            if (job?.isActive != true) job = scope.launch { loop(app) }
        } else {
            job?.cancel()
            job = null
            _status.value = "Off"
        }
    }

    fun unpair(context: Context) {
        Settings(context).telegramChat = 0L
        pairingCode = null
    }

    private suspend fun loop(app: Context) {
        val settings = Settings(app)
        val vault = KeyVault(app)
        val http = OkHttpClient.Builder().readTimeout(70, TimeUnit.SECONDS).build()
        val agent = TelegramAgent(app, http)
        var offset = 0L
        var backoff = 2_000L
        _status.value = "Connecting…"
        while (currentCoroutineContext().isActive) {
            val token = vault.read(KeyVault.TELEGRAM_TOKEN) ?: break
            try {
                val updates = getUpdates(http, token, offset)
                _status.value = if (settings.telegramChat != 0L) "Online" else "Online, waiting to pair"
                backoff = 2_000L
                for (i in 0 until updates.length()) {
                    val update = updates.getJSONObject(i)
                    offset = update.getLong("update_id") + 1
                    update.optJSONObject("message")?.let { handle(app, http, token, it, agent) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BadToken) {
                _status.value = "Telegram rejected the token"
                Diag.w("telegram: token rejected")
                return
            } catch (e: Exception) {
                _status.value = "Offline, retrying…"
                Diag.w("telegram: ${e.javaClass.simpleName}")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    private suspend fun handle(app: Context, http: OkHttpClient, token: String, message: JSONObject, agent: TelegramAgent) {
        val settings = Settings(app)
        val chat = message.getJSONObject("chat").getLong("id")
        val text = message.optString("text").trim()
        val owner = settings.telegramChat
        if (owner == 0L) {
            val code = pairingCode
            val given = Regex("^/pair\\s+(\\d{6})$").find(text)?.groupValues?.get(1)
            if (code != null && given == code) {
                settings.telegramChat = chat
                pairingCode = null
                Diag.i("telegram: paired")
                send(http, token, chat, "Paired. I'm JARVIS on your phone: ask me for the time, weather, an alarm, a reminder, to remember something. /help for more.")
            }
            return // strangers get no reply at all
        }
        if (chat != owner) return
        when {
            text.isEmpty() -> send(http, token, chat, "I only read text for now.")
            text == "/start" || text == "/help" -> send(
                http, token, chat,
                "Ask me anything, or tell me what to do on your phone:\n• what's the time / weather in Cairo\n• set an alarm for 7\n• remind me to call mum at 5\n" +
                    "• remember my key is in the drawer\n• pause the music, volume 30\n• what did I miss?\n/clear forgets this chat's context.",
            )
            text == "/clear" -> {
                agent.clear()
                send(http, token, chat, "Cleared.")
            }
            else -> {
                typing(http, token, chat)
                send(http, token, chat, agent.reply(text))
            }
        }
    }

    private suspend fun getUpdates(http: OkHttpClient, token: String, offset: Long): JSONArray {
        val url = "https://api.telegram.org/bot$token/getUpdates".toHttpUrl().newBuilder()
            .addQueryParameter("timeout", "50")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("allowed_updates", "[\"message\"]")
            .build()
        val body = http.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (response.code == 401 || response.code == 404) throw BadToken()
            if (!response.isSuccessful) throw IOException("http ${response.code}")
            response.body?.string().orEmpty()
        }
        return JSONObject(body).optJSONArray("result") ?: JSONArray()
    }

    private suspend fun send(http: OkHttpClient, token: String, chat: Long, text: String) {
        for (part in text.ifBlank { "…" }.chunked(3_900)) post(http, token, "sendMessage", JSONObject().put("chat_id", chat).put("text", part))
    }

    private suspend fun typing(http: OkHttpClient, token: String, chat: Long) =
        post(http, token, "sendChatAction", JSONObject().put("chat_id", chat).put("action", "typing"))

    private suspend fun post(http: OkHttpClient, token: String, method: String, body: JSONObject) {
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$token/$method")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { http.newCall(request).await().close() }.onFailure {
            if (it is CancellationException) throw it
            Diag.w("telegram: $method failed (${it.javaClass.simpleName})")
        }
    }

    private class BadToken : Exception()
}

/** Waits for an OkHttp call without blocking a thread; cancelling the coroutine cancels the call. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { cancel() }
}
