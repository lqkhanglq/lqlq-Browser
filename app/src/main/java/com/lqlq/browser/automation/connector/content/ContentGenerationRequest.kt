package com.lqlq.browser.automation.connector.content

import com.lqlq.browser.automation.script.ContentDurationPolicy

data class ContentGenerationRequest(
    val providerId: String,
    val model: String,
    val topic: String,
    val language: String,
    val contentType: String,
    val promptTemplate: String,
    val maximumOutputLength: Int,
    val durationPolicy: ContentDurationPolicy? = null,
    val repairInstruction: String? = null
)
