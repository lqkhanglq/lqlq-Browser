package com.lqlq.browser.automation.repository

interface AutomationRepository {
    fun isFoundationReady(): Boolean

    suspend fun createProject(
        project: AutomationProjectRecord
    ): AutomationProjectRecord

    suspend fun getProject(
        projectId: String
    ): AutomationProjectRecord?

    suspend fun saveWorkflowDefinition(
        definition: AutomationWorkflowDefinitionRecord
    ): AutomationWorkflowDefinitionRecord

    suspend fun getWorkflowDefinition(
        workflowId: String,
        workflowVersion: Int
    ): AutomationWorkflowDefinitionRecord?

    suspend fun createJobGraph(
        command: CreateAutomationJobGraphCommand
    ): AutomationJobGraphSnapshot

    suspend fun getJobGraph(
        jobId: String
    ): AutomationJobGraphSnapshot?

    suspend fun listRecentJobs(
        projectId: String,
        limit: Int
    ): List<AutomationJobRecord>

    /**
     * Xoa sach job + step + artifact (DB rows) khoi automation database.
     * Tra ve storageUri cua tung artifact da xoa de caller (facade) tu xoa
     * file that tren disk (artifact store khong biet ve DB row).
     */
    suspend fun deleteJobGraph(
        jobId: String
    ): List<String>

    companion object {
        fun empty(): AutomationRepository = EmptyAutomationRepository
    }
}

private object EmptyAutomationRepository : AutomationRepository {
    override fun isFoundationReady(): Boolean = true

    override suspend fun createProject(
        project: AutomationProjectRecord
    ): AutomationProjectRecord = unsupported()

    override suspend fun getProject(
        projectId: String
    ): AutomationProjectRecord? = unsupported()

    override suspend fun saveWorkflowDefinition(
        definition: AutomationWorkflowDefinitionRecord
    ): AutomationWorkflowDefinitionRecord = unsupported()

    override suspend fun getWorkflowDefinition(
        workflowId: String,
        workflowVersion: Int
    ): AutomationWorkflowDefinitionRecord? = unsupported()

    override suspend fun createJobGraph(
        command: CreateAutomationJobGraphCommand
    ): AutomationJobGraphSnapshot = unsupported()

    override suspend fun getJobGraph(
        jobId: String
    ): AutomationJobGraphSnapshot? = unsupported()

    override suspend fun listRecentJobs(
        projectId: String,
        limit: Int
    ): List<AutomationJobRecord> = unsupported()

    override suspend fun deleteJobGraph(
        jobId: String
    ): List<String> = unsupported()

    private fun <T> unsupported(): T {
        throw UnsupportedOperationException("Empty automation repository does not support storage operations.")
    }
}
