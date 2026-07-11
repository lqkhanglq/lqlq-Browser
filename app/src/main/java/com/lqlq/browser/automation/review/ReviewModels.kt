package com.lqlq.browser.automation.review

data class ReviewCheck(
    val key: String,
    val label: String,
    val passed: Boolean,
    val detail: String
)

data class ReviewState(
    val schema: String = "lqlq.review_state.v1",
    val status: String,
    val checks: List<ReviewCheck>,
    val warnings: List<String>,
    val approvedAtEpochMs: Long? = null,
    val rejectedAtEpochMs: Long? = null,
    val rejectedReason: String? = null
)
