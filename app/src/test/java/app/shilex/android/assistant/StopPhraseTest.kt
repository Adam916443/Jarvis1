package app.shilex.android.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopPhraseTest {
    @Test fun jarvisStopEndsIt() {
        assertTrue(StopPhrase.matches("Jarvis stop."))
        assertTrue(StopPhrase.matches("Hey Jarvis, stop!"))
        assertTrue(StopPhrase.matches("جارفس وقف"))
    }

    @Test fun stopInsideARequestDoesNot() {
        assertFalse(StopPhrase.matches("stop the music"))
        assertFalse(StopPhrase.matches("don't stop at the shop"))
    }
}
