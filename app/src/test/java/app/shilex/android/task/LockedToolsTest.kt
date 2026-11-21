package app.shilex.android.task

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedToolsTest {
    @Test
    fun harmlessToolsRunWhileLocked() {
        listOf("get_current_datetime", "get_weather", "set_alarm", "set_timer", "reminder_set", "media_control", "device_control")
            .forEach { assertTrue(it, Capabilities.allowedWhileLocked(it)) }
    }

    @Test
    fun anythingPersonalOrOutgoingWaitsForUnlock() {
        listOf(
            "call_contact", "sms_send", "whatsapp_send", "email_compose", "reply_send", "reply_prepare",
            "notifications_check", "calendar_list", "calendar_add", "recall", "remember", "forget",
            "ui_read", "ui_click", "ui_type", "ui_tap_seen", "ui_key", "ui_scroll", "hand_to_user",
            "open_app", "book_tickets", "routine_run", "reminders_list", "watch_messages",
        ).forEach { assertFalse(it, Capabilities.allowedWhileLocked(it)) }
    }

    @Test
    fun everyLockedToolIsARealCapability() {
        Capabilities.tools.filter(Capabilities::allowedWhileLocked).forEach { assertNotNull(it, Capabilities.of(it)) }
    }

    @Test
    fun noHighRiskToolIsAllowedWhileLocked() {
        Capabilities.tools
            .filter(Capabilities::allowedWhileLocked)
            .forEach { assertTrue(it, Capabilities.of(it)!!.risk < Risk.Medium) }
    }
}
