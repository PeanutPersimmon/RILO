package io.github.peanutpersimmon.rilo.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryKeywordPolicyTest {
    @Test
    fun normalizationUsesTrimNfkcRootLowercaseAndCollapsedWhitespace() {
        assertEquals("meeting", CategoryKeywordNormalizer.normalize("  ＭＥＥＴＩＮＧ  "))
        assertEquals("a b", CategoryKeywordNormalizer.normalize("A\t  B"))
    }

    @Test
    fun sleepVocabularyIsReservedAtDomainBoundary() {
        assertTrue(ReservedKeywordPolicy.isReserved("午睡"))
        assertTrue(ReservedKeywordPolicy.isReserved("安排小睡一下"))
        assertTrue(ReservedKeywordPolicy.isReserved("睡了一会儿"))
        assertTrue(ReservedKeywordPolicy.isReserved("打个盹"))
        assertFalse(ReservedKeywordPolicy.isReserved("休息"))
        assertFalse(ReservedKeywordPolicy.isReserved("组会"))
    }
}
