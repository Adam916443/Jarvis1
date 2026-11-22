package app.shilex.android.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopPhraseTest {
    @Test fun jarvisStopEndsIt() {
        assertTrue(StopPhrase.matches("Jarvis stop."))
        assertTrue(StopPhrase.matches("Hey Jarvis, please stop it now!"))
        assertTrue(StopPhrase.matches("Stop"))
        assertTrue(StopPhrase.matches("جارفس وقف"))
        assertTrue(StopPhrase.matches("كفاية"))
    }

    @Test fun stopInsideARequestDoesNot() {
        assertFalse(StopPhrase.matches("stop the music"))
        assertFalse(StopPhrase.matches("don't stop at the shop"))
        assertFalse(StopPhrase.matches("what is the time"))
    }
}
