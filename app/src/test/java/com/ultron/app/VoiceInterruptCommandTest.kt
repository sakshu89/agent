package com.ultron.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInterruptCommandTest {
    @Test fun acceptsDirectStopPhrases() {
        listOf("stop", "Ultron stop", "stop speaking", "be quiet", "quiet").forEach {
            assertTrue("Expected interrupt phrase: $it", VoiceInterruptCommand.matches(it))
        }
    }

    @Test fun rejectsOrdinarySentences() {
        listOf("please stop the music app", "don't stop now", "what is a stop word", "hello Ultron").forEach {
            assertFalse("Unexpected interrupt phrase: $it", VoiceInterruptCommand.matches(it))
        }
    }
}
