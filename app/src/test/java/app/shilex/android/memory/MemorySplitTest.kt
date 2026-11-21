package app.shilex.android.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemorySplitTest {
    @Test fun splitsLabelAndValue() {
        assertEquals("mom's name" to "Heba", MemoryRules.split("mom's name → Heba"))
        assertEquals("اسم أمي" to "هبة", MemoryRules.split("اسم أمي → هبة"))
    }

    @Test fun freeTextIsNotSplit() {
        assertNull(MemoryRules.split("parked on level 3"))
        assertNull(MemoryRules.split(" → empty label"))
    }

    @Test fun houseKeyIsNotASecret() {
        assertEquals(false, MemoryRules.looksSecret("المفتاح → في الدرج"))
    }
}
