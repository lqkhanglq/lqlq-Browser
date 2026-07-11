package com.lqlq.browser.automation

data class AutomationPipelineProgress(
    val jobId: String,
    val clientRequestId: String?,
    val topic: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val state: String,
    val message: String
)

fun interface AutomationPipelineProgressListener {
    fun onProgress(progress: AutomationPipelineProgress)

    companion object {
        val NONE = AutomationPipelineProgressListener { }
    }
}
