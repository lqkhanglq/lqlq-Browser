package com.lqlq.browser.automation.visual

import com.lqlq.browser.automation.image.ScenePrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VisualAssetPlannerTest {

    private val planner = HeuristicVisualAssetPlanner()

    @Test
    fun canonicalTopicAndStockQueryStayCleanForPexels() {
        val plans = planner.planAssets(
            VisualAssetPlanRequest(
                topic = "Tao video Shorts 9:16. Bat buoc tao dung 12 scene.\nChu de: 10 cau noi giup ban giao tiep tot hon",
                contentType = "video_script",
                scenePrompts = listOf(
                    ScenePrompt(
                        sceneId = "scene-1",
                        ordinal = 1,
                        summary = "Lang nghe chu dong",
                        visualPrompt = "Vertical short scene 1 about 10 cau noi giup ban giao tiep tot hon.",
                        negativePrompt = null,
                        aspectRatio = "9:16",
                        stockSearchQuery = "active listening meeting",
                        visualDirection = "Cuoc hop nho va tap trung"
                    )
                ),
                selectedProviderId = "pexels",
                selectedProviderCostType = "STOCK_MEDIA",
                targetAspectRatio = "9:16"
            )
        )

        assertEquals("active listening meeting", plans.first().assetQuery)
        assertFalse(plans.first().assetQuery.contains("Tao video"))
        assertFalse(plans.first().assetQuery.contains("Bat buoc"))
    }

    @Test
    fun quotesTopicGetsDiversifiedQueriesAcrossOrdinals() {
        val scenePrompts = (1..12).map { ordinal ->
            ScenePrompt(
                sceneId = "scene-$ordinal",
                ordinal = ordinal,
                summary = "Canh $ordinal",
                visualPrompt = "Visual $ordinal",
                negativePrompt = null,
                aspectRatio = "9:16",
                stockSearchQuery = "giao tiep",
                visualDirection = "giao tiep"
            )
        }

        val plans = planner.planAssets(
            VisualAssetPlanRequest(
                topic = "Chu de: 10 cau noi giup ban giao tiep tot hon",
                contentType = "video_script",
                scenePrompts = scenePrompts,
                selectedProviderId = "pexels",
                selectedProviderCostType = "STOCK_MEDIA",
                targetAspectRatio = "9:16"
            )
        )

        assertEquals(12, plans.map { it.assetQuery }.distinct().size)
        assertEquals("communication intro", plans.first().assetQuery)
        assertEquals("active listening", plans[2].assetQuery)
    }
}
