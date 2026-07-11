package com.lqlq.browser.automation.connector.content

interface ContentGenerationConnector {
    suspend fun testConnection(
        config: ContentProviderConfig
    ): ContentGenerationResult

    suspend fun generateContent(
        config: ContentProviderConfig,
        request: ContentGenerationRequest
    ): ContentGenerationResult
}
