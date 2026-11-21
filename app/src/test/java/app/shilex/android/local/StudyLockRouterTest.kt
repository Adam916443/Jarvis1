package app.shilex.android.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyLockRouterTest {
    @Test fun studentLockIsHeard() {
        assertEquals(LocalCommand.StudyLock, LocalRouter.match("Hey Jarvis student lock"))
        assertEquals(LocalCommand.StudyLock, LocalRouter.match("study mode"))
        assertEquals(LocalCommand.StudyLock, LocalRouter.match("student look"))
    }

    @Test fun otherSentencesAreNot() {
        assertNull(LocalRouter.match("student loan rates"))
    }
}
