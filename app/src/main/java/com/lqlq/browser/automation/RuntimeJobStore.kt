package com.lqlq.browser.automation

import android.content.Context
import java.io.File

/**
 * Luu trang thai runtime cua tung job (scenePrompts, artifacts, cau hinh, chinh sua
 * cua nguoi dung) xuong dia duoi dang JSON, de KHI APP BI KILL/thoat dot ngot roi mo
 * lai van khoi phuc duoc - khong mat cong (vd da cao 20/30 anh, da sua noi dung).
 *
 * Chi lam I/O file (luu/nap/xoa chuoi JSON). Viec dung/parse JSON do AutomationFacade
 * lo (vi RuntimeAutomationJob la class noi bo cua facade).
 */
class RuntimeJobStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "automation-runtime-jobs")

    fun save(jobId: String, json: String) {
        runCatching {
            dir.mkdirs()
            File(dir, fileName(jobId)).writeText(json, Charsets.UTF_8)
        }
    }

    /** Nap tat ca job da luu -> Map<jobId, json>. Bo qua file loi. */
    fun loadAll(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val files = dir.listFiles() ?: return out
        for (file in files) {
            if (!file.isFile || !file.name.endsWith(".json")) continue
            runCatching {
                val json = file.readText(Charsets.UTF_8)
                val jobId = file.nameWithoutExtension
                if (jobId.isNotBlank() && json.isNotBlank()) out[jobId] = json
            }
        }
        return out
    }

    fun delete(jobId: String) {
        runCatching { File(dir, fileName(jobId)).delete() }
    }

    private fun fileName(jobId: String): String {
        val safe = jobId.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "job" }
        return "$safe.json"
    }

    companion object {
        /** Store rong (khong luu gi) - dung khi khong co context (test/empty facade). */
        fun noop(): RuntimeJobStore? = null
    }
}
