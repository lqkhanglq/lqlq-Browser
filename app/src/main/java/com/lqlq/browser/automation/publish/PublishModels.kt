package com.lqlq.browser.automation.publish

data class PublishPlan(
    val schema: String = "lqlq.publish_plan.v1",
    val status: String,
    val targets: List<String>,
    val videoArtifactUri: String,
    val metadataArtifactUri: String,
    val reviewStatus: String,
    val publishMode: String,
    val createdAtEpochMs: Long,
    val publishedAtEpochMs: Long? = null,
    val notes: List<String> = emptyList()
)
