package com.ultron.app.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserResearchTest {
    @Test fun genericKnowledgeRequestBecomesResearch() {
        val command = CommandParser.parse("tell me how diodes work")
        assertTrue(command is Command.Research)
        assertEquals("how diodes work", (command as Command.Research).query)
    }

    @Test fun topicIsNotHardCoded() {
        val command = CommandParser.parse("explain quantum tunneling")
        assertTrue(command is Command.Research)
        assertEquals("quantum tunneling", (command as Command.Research).query)
    }

    @Test fun naturalKnowledgeVariantsAreResearch() {
        assertTrue(CommandParser.parse("can you explain photosynthesis") is Command.Research)
        assertTrue(CommandParser.parse("how to cook rice") is Command.Research)
        assertTrue(CommandParser.parse("what's a transistor") is Command.Research)
    }

    @Test fun explicitSearchStillWinsOverKnowledgeRouting() {
        val command = CommandParser.parse("search how to cook rice on youtube")
        assertTrue(command is Command.Search)
        assertEquals("how to cook rice", (command as Command.Search).query)
    }
}
