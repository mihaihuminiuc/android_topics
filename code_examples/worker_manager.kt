// Example implementation of a WorkManager worker that downloads a file and reports progress,
// plus a small Activity example that enqueues the work and updates the UI.
// Note: add <uses-permission android:name="android.permission.INTERNET"/> in AndroidManifest
// and include WorkManager (work-runtime-ktx) in your Gradle dependencies.

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class FileDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_FILE_URL = "file_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_ERROR_MSG = "error_message"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val urlString = inputData.getString(KEY_FILE_URL)
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR_MSG to "Missing URL"))

        val fileName = inputData.getString(KEY_FILE_NAME) ?: URL(urlString).path.substringAfterLast('/')
        val outputFile = File(applicationContext.cacheDir, fileName)

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
                doInput = true
                connect()
            }

            val contentLength = connection.contentLengthLong
            val input = BufferedInputStream(connection.inputStream)
            val output = FileOutputStream(outputFile)
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var downloaded: Long = 0
            var lastProgress = -1

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloaded += bytesRead

                if (contentLength > 0) {
                    val progress = ((downloaded * 100) / contentLength).toInt()
                    if (progress != lastProgress) {
                        // report progress (0..100)
                        setProgressAsync(workDataOf(KEY_PROGRESS to progress))
                        lastProgress = progress
                    }
                }

                if (isStopped) {
                    input.close()
                    output.close()
                    return@withContext Result.failure(workDataOf(KEY_ERROR_MSG to "Cancelled"))
                }
            }

            output.flush()
            output.close()
            input.close()

            return@withContext Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))
        } catch (e: Exception) {
            return@withContext Result.failure(workDataOf(KEY_ERROR_MSG to (e.message ?: "Download failed")))
        } finally {
            connection?.disconnect()
        }
    }
}

// Simple activity demonstrating how to enqueue the worker and observe progress / result.
class DownloadActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private var workId: UUID? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build a tiny UI programmatically to avoid xml layout here.
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        statusText = TextView(this).apply {
            text = "Idle"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        startButton = Button(this).apply {
            text = "Start download"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        container.addView(progressBar)
        container.addView(statusText)
        container.addView(startButton)

        setContentView(container)

        startButton.setOnClickListener {
            // Replace with a real URL to test. The worker saves to cacheDir to avoid storage permissions.
            startDownload("https://speed.hetzner.de/100MB.bin")
        }
    }

    private fun startDownload(url: String) {
        val request = OneTimeWorkRequestBuilder<FileDownloadWorker>()
            .setInputData(workDataOf(FileDownloadWorker.KEY_FILE_URL to url))
            .build()

        workId = request.id
        WorkManager.getInstance(applicationContext).enqueue(request)
        observeProgress(request.id)
        statusText.text = "Enqueued"
    }

    private fun observeProgress(id: UUID) {
        WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(id)
            .observe(this) { info ->
                if (info == null) return@observe

                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        statusText.text = info.state.name
                    }

                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(FileDownloadWorker.KEY_PROGRESS, 0)
                        progressBar.progress = progress
                        statusText.text = "Downloading: $progress%"
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val path = info.outputData.getString(FileDownloadWorker.KEY_OUTPUT_PATH)
                        statusText.text = "Done: ${path ?: "(unknown path)"}"
                        progressBar.progress = 100
                    }

                    WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString(FileDownloadWorker.KEY_ERROR_MSG) ?: "Unknown error"
                        statusText.text = "Error: $err"
                    }

                    WorkInfo.State.CANCELLED -> {
                        statusText.text = "Cancelled"
                    }

                    else -> {}
                }
            }
    }
}
