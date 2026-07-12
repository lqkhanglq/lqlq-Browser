package com.lqlq.browser.automation.script

data class ContentDurationPolicy(
    val targetItemCount: Int?,
    val targetSceneCount: Int,
    val targetDurationMs: Long,
    val includeIntro: Boolean,
    val includeOutro: Boolean,
    val explicitDuration: Boolean
) {
    companion object {
        private val SECONDS_REGEX = Regex(
            "(\\d{1,3})\\s*(giay|s|sec|secs|second|seconds)",
            RegexOption.IGNORE_CASE
        )
        private val MINUTES_REGEX = Regex(
            "(\\d{1,2})\\s*(phut|min|minute|minutes)",
            RegexOption.IGNORE_CASE
        )

        // KHONG con doan "day co phai listicle khong" bang tu khoa/regex nua - lqlq
        // khong tu doan noi dung, de Gemini tu quyet dinh so muc/co cau truc intro-
        // items-outro hay khong (schema JSON da luon duoc yeu cau, xem
        // GeminiContentConnector.buildPrompt). O day CHI con tinh thoi luong dich va
        // 1 GOI Y so muc (khong bat buoc) de dua vao prompt lam diem khoi dau.
        fun fromTopic(
            topic: String,
            desiredDurationSeconds: Int? = null
        ): ContentDurationPolicy {
            val normalized = normalizeVietnamese(topic.trim())
            val explicitDurationMs = desiredDurationSeconds
                ?.takeIf { it > 0 }
                ?.let { it * 1000L }
                ?: parseExplicitDurationMs(normalized)
            val targetDurationMs = explicitDurationMs ?: 35_000L
            // Goi y so muc uoc luong ~1 muc/7 giay noi dung - CHI la diem khoi dau
            // cho prompt, Gemini duoc yeu cau tu dieu chinh len/xuong theo chu de
            // (xem "Rules" trong buildPrompt).
            val estimatedItemCount = (targetDurationMs / 7_000L).toInt().coerceIn(3, 20)
            return ContentDurationPolicy(
                targetItemCount = estimatedItemCount,
                targetSceneCount = estimatedItemCount + 2,
                targetDurationMs = targetDurationMs,
                includeIntro = true,
                includeOutro = true,
                explicitDuration = explicitDurationMs != null
            )
        }

        private fun parseExplicitDurationMs(topic: String): Long? {
            SECONDS_REGEX.find(topic)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { seconds ->
                return seconds * 1000L
            }
            MINUTES_REGEX.find(topic)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { minutes ->
                return minutes * 60_000L
            }
            return null
        }

        private fun normalizeVietnamese(value: String): String {
            return buildString(value.length) {
                value.lowercase().forEach { character ->
                    append(
                        when (character) {
                            'à', 'á', 'ạ', 'ả', 'ã', 'â', 'ầ', 'ấ', 'ậ', 'ẩ', 'ẫ', 'ă', 'ằ', 'ắ', 'ặ', 'ẳ', 'ẵ' -> 'a'
                            'è', 'é', 'ẹ', 'ẻ', 'ẽ', 'ê', 'ề', 'ế', 'ệ', 'ể', 'ễ' -> 'e'
                            'ì', 'í', 'ị', 'ỉ', 'ĩ' -> 'i'
                            'ò', 'ó', 'ọ', 'ỏ', 'õ', 'ô', 'ồ', 'ố', 'ộ', 'ổ', 'ỗ', 'ơ', 'ờ', 'ớ', 'ợ', 'ở', 'ỡ' -> 'o'
                            'ù', 'ú', 'ụ', 'ủ', 'ũ', 'ư', 'ừ', 'ứ', 'ự', 'ử', 'ữ' -> 'u'
                            'ỳ', 'ý', 'ỵ', 'ỷ', 'ỹ' -> 'y'
                            'đ' -> 'd'
                            else -> character
                        }
                    )
                }
            }
        }
    }
}
