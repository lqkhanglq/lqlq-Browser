package com.lqlq.browser.automation.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.lqlq.browser.automation.database.AutomationDatabase
import com.lqlq.browser.automation.model.AutomationJobStatus
import com.lqlq.browser.automation.model.AutomationStepStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomAutomationRepository(
    private val database: AutomationDatabase,
    private val dependencies: AutomationRepositoryDependencies = AutomationRepositoryDependencies()
) : AutomationRepository {

    override fun isFoundationReady(): Boolean = true

    override suspend fun createProject(
        project: AutomationProjectRecord
    ): AutomationProjectRecord = runStorage {
        validateProject(project)
        try {
            database.projectDao().insert(project.toEntity())
        } catch (error: Throwable) {
            throw mapStorageError(error, "Failed to create project.")
        }
        database.projectDao().getById(project.projectId)?.toRecord()
            ?: throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.STORAGE,
                "Created project could not be read back."
            )
    }

    override suspend fun getProject(
        projectId: String
    ): AutomationProjectRecord? = runStorage {
        requireNotBlank(projectId, "Project ID is required.")
        database.projectDao().getById(projectId)?.toRecord()
    }

    override suspend fun saveWorkflowDefinition(
        definition: AutomationWorkflowDefinitionRecord
    ): AutomationWorkflowDefinitionRecord = runStorage {
        validateWorkflowDefinition(definition)
        try {
            database.workflowDefinitionDao().insert(definition.toEntity())
        } catch (error: Throwable) {
            throw mapStorageError(error, "Failed to save workflow definition.")
        }
        database.workflowDefinitionDao()
            .getByIdAndVersion(definition.workflowId, definition.workflowVersion)
            ?.toRecord()
            ?: throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.STORAGE,
                "Saved workflow definition could not be read back."
            )
    }

    override suspend fun getWorkflowDefinition(
        workflowId: String,
        workflowVersion: Int
    ): AutomationWorkflowDefinitionRecord? = runStorage {
        requireNotBlank(workflowId, "Workflow ID is required.")
        if (workflowVersion <= 0) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Workflow version must be positive."
            )
        }
        database.workflowDefinitionDao()
            .getByIdAndVersion(workflowId, workflowVersion)
            ?.toRecord()
    }

    override suspend fun createJobGraph(
        command: CreateAutomationJobGraphCommand
    ): AutomationJobGraphSnapshot = runStorage {
        validateCreateJobGraphCommand(command)
        try {
            database.withTransaction {
                val project = database.projectDao().getById(command.job.projectId)
                    ?: throw AutomationRepositoryException(
                        AutomationRepositoryErrorCode.NOT_FOUND,
                        "Project was not found."
                    )
                if (project.deletedAtEpochMs != null) {
                    throw AutomationRepositoryException(
                        AutomationRepositoryErrorCode.CONSTRAINT,
                        "Project is soft-deleted."
                    )
                }

                val definition = database.workflowDefinitionDao().getByIdAndVersion(
                    command.job.workflowId,
                    command.job.workflowVersion
                ) ?: throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.NOT_FOUND,
                    "Workflow definition was not found."
                )

                if (definition.workflowId != command.job.workflowId ||
                    definition.workflowVersion != command.job.workflowVersion
                ) {
                    throw AutomationRepositoryException(
                        AutomationRepositoryErrorCode.CONSTRAINT,
                        "Workflow definition mismatch."
                    )
                }

                if (database.jobDao().getById(command.job.jobId) != null) {
                    throw AutomationRepositoryException(
                        AutomationRepositoryErrorCode.CONFLICT,
                        "Job ID already exists."
                    )
                }

                database.jobDao().insert(command.job.toEntity())
                if (command.connectorBindings.isNotEmpty()) {
                    database.connectorBindingDao().insertAll(
                        command.connectorBindings.map { it.toEntity() }
                    )
                }
                database.stepDao().insertAll(command.steps.map { it.toEntity() })
                if (command.dependencies.isNotEmpty()) {
                    database.stepDependencyDao().insertAll(command.dependencies.map { it.toEntity() })
                }

                val initialOutboxEvents = if (command.initialOutboxEvents.isEmpty()) {
                    listOf(createDefaultOutboxEvent(command.job))
                } else {
                    command.initialOutboxEvents
                }
                initialOutboxEvents.forEach { database.outboxDao().insert(it.toEntity()) }
            }
        } catch (error: Throwable) {
            if (error is AutomationRepositoryException) {
                throw error
            }
            throw mapStorageError(error, "Failed to create job graph.")
        }

        getJobGraph(command.job.jobId)
            ?: throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.STORAGE,
                "Created job graph could not be read back."
            )
    }

    override suspend fun getJobGraph(
        jobId: String
    ): AutomationJobGraphSnapshot? = runStorage {
        requireNotBlank(jobId, "Job ID is required.")
        val job = database.jobDao().getById(jobId)?.toRecord() ?: return@runStorage null
        val connectorBindings = (
            database.connectorBindingDao().listByProject(job.projectId) +
                database.connectorBindingDao().listByJob(jobId)
            )
            .distinctBy { it.bindingId }
            .sortedWith(compareBy({ it.createdAtEpochMs }, { it.bindingId }))
        AutomationJobGraphSnapshot(
            job = job,
            connectorBindings = connectorBindings.map { it.toRecord() },
            steps = database.stepDao().listByJob(jobId).map { it.toRecord() },
            dependencies = database.stepDependencyDao().listByJob(jobId).map { it.toRecord() },
            outboxEvents = database.outboxDao().listByAggregate("JOB", jobId).map { it.toRecord() }
        )
    }

    override suspend fun listRecentJobs(
        projectId: String,
        limit: Int
    ): List<AutomationJobRecord> = runStorage {
        requireNotBlank(projectId, "Project ID is required.")
        if (limit <= 0) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "List limit must be positive."
            )
        }
        database.jobDao().listRecentByProject(projectId, limit).map { it.toRecord() }
    }

    override suspend fun deleteJobGraph(
        jobId: String
    ): List<String> = runStorage {
        requireNotBlank(jobId, "Job ID is required.")
        database.withTransaction {
            val artifactUris = database.artifactDao().listByJob(jobId).map { it.storageUri }
            // Thu tu XOA THU CONG BAT BUOC (khong the dua vao CASCADE tu-dong cua
            // jobId vi co 2 rang buoc FK NO_ACTION cheo nhau gay loi
            // SQLITE_CONSTRAINT_FOREIGNKEY neu xoa sai thu tu — day la ly do truoc
            // day xoa duoc phien DRAFT (chua co job) nhung xoa phien DA CO job thi
            // luon that bai:
            //   1) automation_artifacts.producerStepId -> automation_steps.stepId (NO_ACTION)
            //      => phai xoa ARTIFACTS truoc STEPS.
            //   2) automation_steps.connectorBindingId -> automation_connector_bindings.bindingId (NO_ACTION)
            //      => phai xoa STEPS truoc CONNECTOR_BINDINGS.
            database.artifactDao().deleteByJob(jobId)
            database.stepDao().deleteByJob(jobId)
            database.connectorBindingDao().deleteByJob(jobId)
            database.outboxDao().deleteByAggregate("JOB", jobId)
            database.jobDao().deleteById(jobId)
            artifactUris
        }
    }

    private suspend fun <T> runStorage(block: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            block()
        }
    }

    private fun validateProject(project: AutomationProjectRecord) {
        requireNotBlank(project.projectId, "Project ID is required.")
        requireNotBlank(project.name, "Project name is required.")
        if (project.configSchemaVersion <= 0) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Project config schema version must be positive."
            )
        }
        validateTimestamp(project.createdAtEpochMs, "Project created time is invalid.")
        validateTimestamp(project.updatedAtEpochMs, "Project updated time is invalid.")
        if (project.updatedAtEpochMs < project.createdAtEpochMs) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Project updated time must not be earlier than created time."
            )
        }
        if (project.deletedAtEpochMs != null && project.deletedAtEpochMs < project.createdAtEpochMs) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Project deleted time must not be earlier than created time."
            )
        }
    }

    private fun validateWorkflowDefinition(definition: AutomationWorkflowDefinitionRecord) {
        requireNotBlank(definition.workflowId, "Workflow ID is required.")
        if (definition.workflowVersion <= 0) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Workflow version must be positive."
            )
        }
        if (definition.definitionSchemaVersion <= 0) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Definition schema version must be positive."
            )
        }
        if (definition.minimumAppVersionCode < 0 || definition.seededFromAppVersionCode < 0) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "App version codes must not be negative."
            )
        }
        requireNotBlank(definition.stepContractJson, "Step contract JSON is required.")
        requireNotBlank(definition.dependencyContractJson, "Dependency contract JSON is required.")
        validateTimestamp(definition.insertedAtEpochMs, "Workflow inserted time is invalid.")
    }

    private fun validateCreateJobGraphCommand(command: CreateAutomationJobGraphCommand) {
        val job = command.job
        requireNotBlank(job.jobId, "Job ID is required.")
        requireNotBlank(job.projectId, "Project ID is required.")
        requireNotBlank(job.workflowId, "Workflow ID is required.")
        if (job.workflowVersion <= 0) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Workflow version must be positive."
            )
        }
        if (command.steps.isEmpty()) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Job graph must contain at least one step."
            )
        }
        if (job.status == AutomationJobStatus.COMPLETED.name) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Completed jobs cannot be created."
            )
        }
        if (job.revision < 0L) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                "Job revision must not be negative."
            )
        }
        validateTimestamp(job.createdAtEpochMs, "Job created time is invalid.")

        val stepIds = hashSetOf<String>()
        val stepKeys = hashSetOf<String>()
        command.steps.forEach { step ->
            requireNotBlank(step.stepId, "Step ID is required.")
            requireNotBlank(step.stepKey, "Step key is required.")
            if (!stepIds.add(step.stepId)) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Duplicate step ID is not allowed."
                )
            }
            if (!stepKeys.add(step.stepKey)) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Duplicate step key is not allowed."
                )
            }
            if (step.jobId != job.jobId) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Step job ID must match the graph job ID."
                )
            }
            if (step.attemptCount != 0) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "New steps must start with attemptCount = 0."
                )
            }
            if (step.revision < 0L) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Step revision must not be negative."
                )
            }
            if (step.executionLeaseOwner != null ||
                step.executionLeaseExpiresAtEpochMs != null ||
                step.executionLeaseBootMarker != null
            ) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "New steps must not start with an execution lease."
                )
            }
            if (step.startedAtEpochMs != null || step.completedAtEpochMs != null) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "New steps must not start in a started or completed state."
                )
            }
            if (step.status != AutomationStepStatus.PENDING.name) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "New steps must start in PENDING status."
                )
            }
        }

        val bindingIds = hashSetOf<String>()
        command.connectorBindings.forEach { binding ->
            requireNotBlank(binding.bindingId, "Binding ID is required.")
            if (!bindingIds.add(binding.bindingId)) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Duplicate binding ID is not allowed."
                )
            }
            when (binding.bindingScope) {
                "PROJECT" -> {
                    if (binding.projectId != job.projectId || binding.jobId != null) {
                        throw AutomationRepositoryException(
                            AutomationRepositoryErrorCode.VALIDATION,
                            "Project-scoped binding must target the command project only."
                        )
                    }
                }
                "JOB" -> {
                    if (binding.jobId != job.jobId) {
                        throw AutomationRepositoryException(
                            AutomationRepositoryErrorCode.VALIDATION,
                            "Job-scoped binding must target the command job."
                        )
                    }
                    if (binding.projectId != null && binding.projectId != job.projectId) {
                        throw AutomationRepositoryException(
                            AutomationRepositoryErrorCode.VALIDATION,
                            "Job-scoped binding project must match the command project when present."
                        )
                    }
                }
                else -> throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Binding scope is invalid."
                )
            }
        }

        command.steps.forEach { step ->
            if (step.connectorBindingId != null && step.connectorBindingId !in bindingIds) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Step references a connector binding that is not in the command."
                )
            }
        }

        val dependencyIds = hashSetOf<String>()
        command.dependencies.forEach { dependency ->
            requireNotBlank(dependency.dependencyId, "Dependency ID is required.")
            if (!dependencyIds.add(dependency.dependencyId)) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Duplicate dependency ID is not allowed."
                )
            }
            if (dependency.fromStepId == dependency.toStepId) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Dependency cannot point to the same step."
                )
            }
            if (dependency.jobId != job.jobId) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Dependency job ID must match the graph job ID."
                )
            }
            if (dependency.fromStepId !in stepIds || dependency.toStepId !in stepIds) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Dependency must reference steps from the same command."
                )
            }
        }

        val outboxEvents = if (command.initialOutboxEvents.isEmpty()) {
            listOf(createDefaultOutboxEvent(job))
        } else {
            command.initialOutboxEvents
        }
        val outboxIds = hashSetOf<String>()
        val dedupeKeys = hashSetOf<String>()
        outboxEvents.forEach { event ->
            requireNotBlank(event.outboxEventId, "Outbox event ID is required.")
            requireNotBlank(event.dedupeKey, "Outbox dedupe key is required.")
            if (!outboxIds.add(event.outboxEventId)) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Duplicate outbox event ID is not allowed."
                )
            }
            if (!dedupeKeys.add(event.dedupeKey)) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Duplicate outbox dedupe key is not allowed."
                )
            }
            if (event.aggregateId != job.jobId || event.aggregateType != "JOB") {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "Outbox aggregate must target the command job."
                )
            }
            if (event.attemptCount != 0) {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "New outbox events must start with attemptCount = 0."
                )
            }
            if (event.status != "PENDING") {
                throw AutomationRepositoryException(
                    AutomationRepositoryErrorCode.VALIDATION,
                    "New outbox events must start in PENDING status."
                )
            }
        }
    }

    private fun createDefaultOutboxEvent(job: AutomationJobRecord): AutomationOutboxEventRecord {
        val now = dependencies.clock.nowEpochMs()
        return AutomationOutboxEventRecord(
            outboxEventId = dependencies.idGenerator.newId(),
            eventType = "JOB_CREATED",
            aggregateType = "JOB",
            aggregateId = job.jobId,
            payloadJson = "{\"jobId\":\"${job.jobId}\"}",
            payloadSchemaVersion = 1,
            dedupeKey = "job-created:${job.jobId}",
            status = "PENDING",
            availableAtEpochMs = now,
            claimedAtEpochMs = null,
            claimOwner = null,
            claimExpiresAtEpochMs = null,
            dispatchedWorkName = null,
            processedAtEpochMs = null,
            attemptCount = 0,
            lastError = null,
            deadLetteredAtEpochMs = null,
            revision = 0L
        )
    }

    private fun mapStorageError(
        error: Throwable,
        fallbackMessage: String
    ): AutomationRepositoryException {
        val message = error.message.orEmpty()
        if (error is AutomationRepositoryException) {
            return error
        }
        return when {
            error is SQLiteConstraintException && message.contains("UNIQUE", ignoreCase = true) ->
                AutomationRepositoryException(
                    AutomationRepositoryErrorCode.CONFLICT,
                    "A unique record already exists.",
                    error
                )

            error is SQLiteConstraintException && message.contains("FOREIGN KEY", ignoreCase = true) ->
                AutomationRepositoryException(
                    AutomationRepositoryErrorCode.CONSTRAINT,
                    "A required related record is missing.",
                    error
                )

            message.contains("UNIQUE", ignoreCase = true) ->
                AutomationRepositoryException(
                    AutomationRepositoryErrorCode.CONFLICT,
                    "A unique record already exists.",
                    error
                )

            message.contains("FOREIGN KEY", ignoreCase = true) ->
                AutomationRepositoryException(
                    AutomationRepositoryErrorCode.CONSTRAINT,
                    "A required related record is missing.",
                    error
                )

            else -> AutomationRepositoryException(
                AutomationRepositoryErrorCode.STORAGE,
                fallbackMessage,
                error
            )
        }
    }

    private fun requireNotBlank(value: String, message: String) {
        if (value.isBlank()) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                message
            )
        }
    }

    private fun validateTimestamp(value: Long, message: String) {
        if (value < 0L) {
            throw AutomationRepositoryException(
                AutomationRepositoryErrorCode.VALIDATION,
                message
            )
        }
    }
}
