package com.localglasses.app.agent.model

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.localglasses.app.LocalGlassesApp
import kotlinx.coroutines.CancellationException

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? LocalGlassesApp ?: return Result.failure()
        val repository = app.agentContainer.modelRepository
        repository.refresh()
        if (repository.isReady()) {
            return Result.success()
        }
        val result = try {
            repository.download { written, total ->
                setProgressAsync(
                    workDataOf(
                        KEY_WRITTEN to written,
                        KEY_TOTAL to total,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            repository.refresh()
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }

        return when {
            result.isSuccess -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "agent-model-download"
        const val KEY_WRITTEN = "written"
        const val KEY_TOTAL = "total"
        private const val MAX_ATTEMPTS = 3
    }
}
