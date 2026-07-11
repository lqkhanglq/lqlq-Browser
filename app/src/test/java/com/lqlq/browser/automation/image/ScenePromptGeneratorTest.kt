package com.lqlq.browser.automation.image

import com.lqlq.browser.automation.script.ContentDurationPolicy
import com.lqlq.browser.automation.script.ScriptSegmentKind
import com.lqlq.browser.automation.script.StructuredScript
import com.lqlq.browser.automation.script.StructuredScriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePromptGeneratorTest {

    private val generator = ScriptScenePromptGenerator()

    @Test
    fun generateScenePromptsReturnsOrderedIndependentPrompts() = runBlocking {
        val prompts = generator.generateScenePrompts(
            ScenePromptGenerationRequest(
                topic = "5 thoi quen buoi sang",
                generatedScript = "Mo dau gay chu y. Thoi quen thu nhat la uong nuoc. Thoi quen thu hai la van dong nhe. Thoi quen thu ba la lap ke hoach ngan.",
                language = "vi",
                visualStyle = "cinematic",
                targetAspectRatio = "9:16",
                requestedSceneCount = 3
            )
        )

        assertEquals(3, prompts.size)
        assertEquals(listOf(1, 2, 3), prompts.map { it.ordinal })
        assertTrue(prompts.all { it.aspectRatio == "9:16" })
        assertTrue(prompts.all { it.visualPrompt.contains("Vertical short scene") })
        assertTrue(prompts.all { it.visualPrompt.contains("5 thoi quen buoi sang") })
        assertTrue(prompts.all { it.voiceText.isNotBlank() })
        assertTrue(prompts.all { it.plannedDurationMs > 0L })
        assertFalse(prompts.any { it.visualPrompt.contains("Generate highly detailed cinematic image prompt in JSON") })
    }

    @Test
    fun structuredListicleCreatesIntroItemsAndOutroWithoutMerging() = runBlocking {
        val policy = ContentDurationPolicy.fromTopic("10 cau noi giup ban giao tiep tot hon")
        val prompts = generator.generateScenePrompts(
            ScenePromptGenerationRequest(
                topic = "10 cau noi giup ban giao tiep tot hon",
                generatedScript = "ignored when structured script exists",
                language = "vi",
                visualStyle = "cinematic",
                targetAspectRatio = "9:16",
                requestedSceneCount = policy.targetSceneCount,
                structuredScript = StructuredScript(
                    policy = policy,
                    segments = buildList {
                        add(
                            StructuredScriptSegment(
                                kind = ScriptSegmentKind.INTRO,
                                index = null,
                                title = "Intro",
                                voiceText = "Hom nay la 10 cau noi giup ban giao tiep tot hon.",
                                onScreenText = "10 cau noi giao tiep",
                                visualQuery = "intro giao tiep",
                                durationMs = 4_000L
                            )
                        )
                        repeat(10) { index ->
                            add(
                                StructuredScriptSegment(
                                    kind = ScriptSegmentKind.ITEM,
                                    index = index + 1,
                                    title = "Cau ${index + 1}",
                                    voiceText = "Cau ${index + 1}. Day la giai thich ngan cho cau ${index + 1}.",
                                    onScreenText = "Cau ${index + 1}",
                                    visualQuery = "giao tiep cau ${index + 1}",
                                    durationMs = 5_000L
                                )
                            )
                        }
                        add(
                            StructuredScriptSegment(
                                kind = ScriptSegmentKind.OUTRO,
                                index = null,
                                title = "Outro",
                                voiceText = "Hay luu lai va luyen tap moi ngay.",
                                onScreenText = "Luyen tap moi ngay",
                                visualQuery = "outro giao tiep",
                                durationMs = 4_000L
                            )
                        )
                    },
                    rawResponse = "{}"
                )
            )
        )

        assertEquals(12, prompts.size)
        assertEquals((1..12).toList(), prompts.map { it.ordinal })
        assertTrue(prompts.count { it.voiceText.contains("Cau ") } >= 10)
        assertTrue(prompts.all { it.voiceText.isNotBlank() })
    }

    @Test
    fun sceneBlocksAreParsedBeforeSentenceSplit() = runBlocking {
        val script = buildString {
            repeat(12) { index ->
                appendLine("Scene so: ${index + 1}")
                appendLine("Loai scene: B-roll")
                appendLine("Thoi luong de xuat: 5 giay")
                appendLine("Tu khoa tim anh/video: giao tiep cau ${index + 1}")
                appendLine("Mo ta visual: Nhan vat giao tiep tu tin o boi canh sang rong.")
                appendLine("Loi doc: Day la loi doc cho scene ${index + 1}.")
                appendLine()
            }
        }

        val prompts = generator.generateScenePrompts(
            ScenePromptGenerationRequest(
                topic = "video giao tiep 9:16 60 giay 12 scene",
                generatedScript = script,
                language = "vi",
                visualStyle = "cinematic",
                targetAspectRatio = "9:16",
                requestedSceneCount = 12
            )
        )

        assertEquals(12, prompts.size)
        assertEquals((1..12).toList(), prompts.map { it.ordinal })
        assertTrue(prompts.first().visualPrompt.contains("giao tiep cau 1"))
        assertTrue(prompts.first().voiceText.contains("scene 1"))
        assertEquals("giao tiep cau 1", prompts.first().stockSearchQuery)
        assertEquals("Nhan vat giao tiep tu tin o boi canh sang rong.", prompts.first().visualDirection)
    }

    @Test
    fun canonicalTopicUsesShortTopicLineInsteadOfFullPrompt() = runBlocking {
        val prompts = generator.generateScenePrompts(
            ScenePromptGenerationRequest(
                topic = "Tao video Shorts 9:16. Bat buoc tao dung 12 scene.\nChu de: 10 cau noi giup ban giao tiep tot hon",
                generatedScript = "Noi dung ngan de tao mot scene duy nhat.",
                language = "vi",
                visualStyle = "cinematic",
                targetAspectRatio = "9:16",
                requestedSceneCount = 1
            )
        )

        assertTrue(prompts.first().visualPrompt.contains("10 cau noi giup ban giao tiep tot hon"))
        assertFalse(prompts.first().visualPrompt.contains("Bat buoc tao dung 12 scene"))
    }
}
