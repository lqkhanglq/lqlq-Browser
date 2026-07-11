package com.lqlq.browser.automation.connector.content

data class ContentProviderConfig(
    val providerId: String,
    val apiKey: String,
    val model: String,
    val promptTemplate: String = "",
    val maximumOutputLength: Int = 12_000
)
