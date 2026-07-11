package com.lqlq.browser.automation.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredScriptParserTest {

    @Test
    fun parseJsonListicleReturnsAllRequestedItems() {
        val policy = ContentDurationPolicy.fromTopic("10 cau noi giup ban giao tiep tot hon")
        val parsed = StructuredScriptParser.parse(
            """
            {
              "intro": { "title": "Intro", "voiceText": "Mo dau ngan", "onScreenText": "Mo dau", "visualQuery": "intro", "durationMs": 4000 },
              "items": [
                {"index": 1, "title": "Cau 1", "voiceText": "Noi dung 1", "onScreenText": "Cau 1", "visualQuery": "v1", "durationMs": 5000},
                {"index": 2, "title": "Cau 2", "voiceText": "Noi dung 2", "onScreenText": "Cau 2", "visualQuery": "v2", "durationMs": 5000},
                {"index": 3, "title": "Cau 3", "voiceText": "Noi dung 3", "onScreenText": "Cau 3", "visualQuery": "v3", "durationMs": 5000},
                {"index": 4, "title": "Cau 4", "voiceText": "Noi dung 4", "onScreenText": "Cau 4", "visualQuery": "v4", "durationMs": 5000},
                {"index": 5, "title": "Cau 5", "voiceText": "Noi dung 5", "onScreenText": "Cau 5", "visualQuery": "v5", "durationMs": 5000},
                {"index": 6, "title": "Cau 6", "voiceText": "Noi dung 6", "onScreenText": "Cau 6", "visualQuery": "v6", "durationMs": 5000},
                {"index": 7, "title": "Cau 7", "voiceText": "Noi dung 7", "onScreenText": "Cau 7", "visualQuery": "v7", "durationMs": 5000},
                {"index": 8, "title": "Cau 8", "voiceText": "Noi dung 8", "onScreenText": "Cau 8", "visualQuery": "v8", "durationMs": 5000},
                {"index": 9, "title": "Cau 9", "voiceText": "Noi dung 9", "onScreenText": "Cau 9", "visualQuery": "v9", "durationMs": 5000},
                {"index": 10, "title": "Cau 10", "voiceText": "Noi dung 10", "onScreenText": "Cau 10", "visualQuery": "v10", "durationMs": 5000}
              ],
              "outro": { "title": "Outro", "voiceText": "Ket lai ngan", "onScreenText": "Ket lai", "visualQuery": "outro", "durationMs": 4000 }
            }
            """.trimIndent(),
            policy
        )

        assertNotNull(parsed)
        assertTrue((parsed?.itemCount ?: 0) >= 10)
        assertTrue((parsed?.sceneCount ?: 0) >= 10)
    }

    @Test
    fun parseFallbackNumberedTextDetectsMissingItems() {
        val policy = ContentDurationPolicy.fromTopic("10 cau noi giup ban giao tiep tot hon")
        val parsed = StructuredScriptParser.parse(
            """
            1. Cau noi thu nhat
            2. Cau noi thu hai
            3. Cau noi thu ba
            """.trimIndent(),
            policy
        )

        assertNotNull(parsed)
        assertEquals(3, parsed?.itemCount)
    }

    @Test
    fun nonListiclePlainTextDoesNotRequireStructuredItems() {
        val policy = ContentDurationPolicy.fromTopic("video dong luc buoi sang")
        val parsed = StructuredScriptParser.parse("Noi dung mo ta thong thuong cho video.", policy)

        assertNotNull(parsed)
        assertEquals(1, parsed?.itemCount)
        assertNull(parsed?.segments?.firstOrNull()?.index?.takeIf { it > 1 })
    }
}
