package com.ultron.app

import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchAnswerSynthesisTest {
    @Test fun selectsRelevantReadableText() {
        val answer = ResearchAnswerSynthesis.summarize(
            "how diodes work",
            listOf("Images", "A diode is a semiconductor device that allows current to flow mainly in one direction.")
        )
        assertTrue(answer.contains("diode", ignoreCase = true))
    }
}
