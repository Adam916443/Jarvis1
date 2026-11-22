package app.shilex.android.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shilex.android.Branding
import app.shilex.android.assistant.VoiceService
import app.shilex.android.data.KeyVault
import app.shilex.android.data.Settings
import app.shilex.android.telegram.TelegramBot

/** Settings for controlling the assistant from a Telegram bot. */
@Composable
fun ColumnScope.TelegramSettings() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val vault = remember { KeyVault(context) }
    val status by TelegramBot.status.collectAsStateWithLifecycle()
    var on by remember { mutableStateOf(settings.telegramOn) }
    var hasToken by remember { mutableStateOf(vault.read(KeyVault.TELEGRAM_TOKEN) != null) }
    var paired by remember { mutableStateOf(settings.telegramChat != 0L) }
    var code by remember { mutableStateOf(TelegramBot.pairingCode) }
    var token by remember { mutableStateOf("") }
    val apply = {
        TelegramBot.sync(context)
        VoiceService.start(context)
        paired = settings.telegramChat != 0L
    }

    if (!hasToken) {
        InfoRow("Bot token", "In Telegram, open @BotFather, send /newbot, and paste the token it gives you. It is stored encrypted on this phone.") {}
        OutlinedTextField(
            token, { token = it.trim() },
            label = { Text("Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        TextButton(onClick = {
            if (token.contains(':')) {
                vault.write(KeyVault.TELEGRAM_TOKEN, token)
                token = ""
                hasToken = true
            }
        }) { Text("Save token") }
        return
    }
    SwitchRow(
        "Control from Telegram",
        "Message your bot to ask ${Branding.ASSISTANT_NAME} things or set alarms, timers and reminders. Only the paired chat is answered. Status: $status",
        on,
    ) { value ->
        on = value
        settings.telegramOn = value
        apply()
    }
    if (!paired) {
        InfoRow(
            "Pair your chat",
            code?.let { "Open your bot in Telegram and send:  /pair $it" } ?: "Tap Pair, then send the code to your bot.",
        ) {
            TextButton(onClick = {
                code = TelegramBot.newPairingCode()
                if (!on) {
                    on = true
                    settings.telegramOn = true
                }
                apply()
            }) { Text("Pair") }
        }
    } else {
        InfoRow("Paired", "Your chat is linked. Nobody else gets a reply.") {
            TextButton(onClick = {
                TelegramBot.unpair(context)
                paired = false
                code = null
            }) { Text("Unpair") }
        }
    }
    InfoRow("Bot token", "Remove the token and switch Telegram off.") {
        TextButton(onClick = {
            vault.remove(KeyVault.TELEGRAM_TOKEN)
            TelegramBot.unpair(context)
            settings.telegramOn = false
            on = false
            hasToken = false
            paired = false
            code = null
            apply()
        }) { Text("Remove") }
    }
}
