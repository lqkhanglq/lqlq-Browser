package com.lqlq.browser.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutomationCleanupSmokeTest {

    @Test
    fun gatewayArtifactsAreGoneAndCleartextRemainsBlocked() {
        val root = sequenceOf(File("."), File(".."))
            .map { it.canonicalFile }
            .first { File(it, "app/src/main/AndroidManifest.xml").exists() }

        assertFalse(File(root, "automation-stack").exists())
        assertFalse(File(root, "app/src/main/java/com/lqlq/browser/automation/connector/remote").exists())
        assertFalse(File(root, "app/src/test/java/com/lqlq/browser/automation/connector/remote").exists())
        assertFalse(File(root, "app/src/debug/AndroidManifest.xml").exists())

        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val js = File(root, "app/src/main/assets/www/v33-automation-center.js").readText()
        val html = File(root, "app/src/main/assets/www/index.html").readText()

        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertFalse(js.contains("getAutomationServerConfig"))
        assertFalse(js.contains("saveAutomationServerConfig"))
        assertFalse(js.contains("testAutomationServerConnection"))
        assertFalse(html.contains("automationServerUrl"))
        assertFalse(html.contains("automationContentService"))
    }
}
