package com.plushledger.update

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.plushledger.sync.AppVersionInfo
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

class AppUpdateManager(private val activity: FragmentActivity) {
    private val preferences = activity.getSharedPreferences(UpdateDownloadWorker.PREFS_NAME, Context.MODE_PRIVATE)
    private val workManager = WorkManager.getInstance(activity.applicationContext)
    private var observedWorkId: UUID? = null
    private val handledTerminalWorkIds = mutableSetOf<UUID>()

    var uiState by mutableStateOf(UpdateDownloadUiState())
        private set

    fun register() {
        cleanupLegacyPendingState()
        if (preferences.getBoolean(UpdateDownloadWorker.KEY_DOWNLOAD_READY, false)) {
            resumeReadyDownload()
        } else {
            savedWorkId()?.let(::observeWork)
        }
    }

    fun unregister() = Unit

    fun download(info: AppVersionInfo) {
        if (uiState.isActive) {
            showToast("更新包已经在下载，请查看当前进度", Toast.LENGTH_SHORT)
            return
        }
        val preference = activity.getSharedPreferences("plush_user_settings", Context.MODE_PRIVATE)
            .getString("download_line", "国内优先")
            .orEmpty()
        val sources = UpdateSourceSelector.order(
            listOfNotNull(info.apkUrl, info.backupApkUrl),
            preference
        )
        if (sources.isEmpty()) {
            showToast("更新地址无效，请稍后重试", Toast.LENGTH_LONG)
            return
        }
        if (info.sha256.length != SHA256_LENGTH) {
            showToast("更新包缺少安全校验信息", Toast.LENGTH_LONG)
            return
        }

        val outputFile = updateFile(info.versionName)
        preferences.edit()
            .putString(UpdateDownloadWorker.KEY_PRIMARY_URL, sources.first())
            .putString(UpdateDownloadWorker.KEY_BACKUP_URL, sources.getOrNull(1))
            .putString(UpdateDownloadWorker.KEY_SHA256, info.sha256.lowercase())
            .putString(UpdateDownloadWorker.KEY_VERSION_NAME, info.versionName)
            .putString(UpdateDownloadWorker.KEY_DOWNLOAD_FILE, outputFile.absolutePath)
            .putBoolean(UpdateDownloadWorker.KEY_DOWNLOAD_READY, false)
            .remove(UpdateDownloadWorker.KEY_LAST_ERROR)
            .apply()

        uiState = UpdateDownloadUiState(
            phase = UpdateDownloadPhase.QUEUED,
            versionName = info.versionName,
            message = "正在建立安全连接"
        )
        requestNotificationPermissionIfNeeded()
        enqueueSavedDownload()
    }

    fun cancelDownload() {
        workManager.cancelUniqueWork(UpdateDownloadWorker.UNIQUE_WORK_NAME)
        uiState = uiState.copy(
            phase = UpdateDownloadPhase.CANCELLED,
            message = "下载已取消，已完成部分会保留用于下次续传"
        )
    }

    fun retryDownload() {
        if (savedSources().isEmpty() || savedOutputFile() == null) {
            uiState = uiState.copy(phase = UpdateDownloadPhase.FAILED, message = "更新信息已失效，请重新检查版本")
            return
        }
        uiState = UpdateDownloadUiState(
            phase = UpdateDownloadPhase.QUEUED,
            versionName = preferences.getString(UpdateDownloadWorker.KEY_VERSION_NAME, "").orEmpty(),
            message = "正在重新建立连接"
        )
        enqueueSavedDownload()
    }

    fun dismissDownloadStatus() {
        if (!uiState.isActive) uiState = UpdateDownloadUiState()
    }

    private fun enqueueSavedDownload() {
        val sources = savedSources()
        val expectedSha = preferences.getString(UpdateDownloadWorker.KEY_SHA256, null).orEmpty()
        val versionName = preferences.getString(UpdateDownloadWorker.KEY_VERSION_NAME, null).orEmpty()
        val outputFile = savedOutputFile()
        if (sources.isEmpty() || expectedSha.length != SHA256_LENGTH || versionName.isBlank() || outputFile == null) {
            uiState = UpdateDownloadUiState(
                phase = UpdateDownloadPhase.FAILED,
                versionName = versionName,
                message = "更新信息不完整，请重新检查版本"
            )
            return
        }

        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    UpdateDownloadWorker.KEY_PRIMARY_URL to sources.first(),
                    UpdateDownloadWorker.KEY_BACKUP_URL to sources.getOrNull(1),
                    UpdateDownloadWorker.KEY_SHA256 to expectedSha,
                    UpdateDownloadWorker.KEY_VERSION_NAME to versionName,
                    UpdateDownloadWorker.KEY_OUTPUT_PATH to outputFile.absolutePath
                )
            )
            .addTag(UpdateDownloadWorker.UNIQUE_WORK_NAME)
            .build()

        preferences.edit().putString(UpdateDownloadWorker.KEY_WORK_ID, request.id.toString()).apply()
        handledTerminalWorkIds.remove(request.id)
        workManager.enqueueUniqueWork(
            UpdateDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        observeWork(request.id)
    }

    private fun observeWork(id: UUID) {
        if (observedWorkId == id) return
        observedWorkId = id
        workManager.getWorkInfoByIdLiveData(id).observe(activity, Observer { info ->
            if (info == null || savedWorkId() != id) return@Observer
            val versionName = preferences.getString(UpdateDownloadWorker.KEY_VERSION_NAME, "").orEmpty()
            when (info.state) {
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED -> uiState = UpdateDownloadUiState(
                    phase = UpdateDownloadPhase.QUEUED,
                    versionName = versionName,
                    message = if (info.state == WorkInfo.State.BLOCKED) "等待网络连接" else "等待系统启动下载任务"
                )
                WorkInfo.State.RUNNING -> {
                    val progress = info.progress.getInt(UpdateDownloadWorker.KEY_PROGRESS, -1)
                    val downloaded = info.progress.getLong(UpdateDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
                    val total = info.progress.getLong(UpdateDownloadWorker.KEY_TOTAL_BYTES, -1L)
                    val status = info.progress.getString(UpdateDownloadWorker.KEY_STATUS)
                        ?: if (progress >= 100) "正在校验安装包" else "正在接收安装包"
                    uiState = UpdateDownloadUiState(
                        phase = if (progress >= 100) UpdateDownloadPhase.VERIFYING else UpdateDownloadPhase.DOWNLOADING,
                        versionName = versionName,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        message = status
                    )
                }
                WorkInfo.State.SUCCEEDED -> if (handledTerminalWorkIds.add(id)) {
                    uiState = UpdateDownloadUiState(
                        phase = UpdateDownloadPhase.VERIFYING,
                        versionName = versionName,
                        progress = 100,
                        message = "安全校验完成，正在打开系统安装器"
                    )
                    resumeReadyDownload()
                }
                WorkInfo.State.FAILED -> if (handledTerminalWorkIds.add(id)) {
                    val error = info.outputData.getString(UpdateDownloadWorker.KEY_ERROR)
                        ?: preferences.getString(UpdateDownloadWorker.KEY_LAST_ERROR, null)
                        ?: "请检查网络后重试"
                    uiState = UpdateDownloadUiState(
                        phase = UpdateDownloadPhase.FAILED,
                        versionName = versionName,
                        message = error
                    )
                }
                WorkInfo.State.CANCELLED -> if (handledTerminalWorkIds.add(id)) {
                    uiState = UpdateDownloadUiState(
                        phase = UpdateDownloadPhase.CANCELLED,
                        versionName = versionName,
                        message = "下载已取消，已完成部分会保留用于下次续传"
                    )
                }
            }
        })
    }

    private fun cleanupLegacyPendingState() {
        if (savedWorkId() != null || preferences.getBoolean(UpdateDownloadWorker.KEY_DOWNLOAD_READY, false)) return
        preferences.edit()
            .remove("download_id")
            .remove("waiting_install_permission")
            .remove("permission_requested")
            .apply()
    }

    private fun resumeReadyDownload() {
        val outputFile = savedOutputFile() ?: return
        val expectedSha = preferences.getString(UpdateDownloadWorker.KEY_SHA256, null) ?: return
        if (!verifyFile(outputFile, expectedSha)) {
            preferences.edit().putBoolean(UpdateDownloadWorker.KEY_DOWNLOAD_READY, false).apply()
            uiState = UpdateDownloadUiState(
                phase = UpdateDownloadPhase.FAILED,
                versionName = preferences.getString(UpdateDownloadWorker.KEY_VERSION_NAME, "").orEmpty(),
                message = "更新包校验失败，请重新下载"
            )
            return
        }
        openInstaller(outputFile)
    }

    private fun openInstaller(outputFile: File) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
            showToast("下载完成，请允许安装未知应用后返回继续", Toast.LENGTH_LONG)
            return
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            outputFile
        )
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        uiState = UpdateDownloadUiState()
        clearReadyState()
    }

    private fun updateFile(versionName: String): File {
        val directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(activity.filesDir, "updates")
        directory.mkdirs()
        return File(directory, "rongrong-ledger-$versionName.apk")
    }

    private fun savedSources(): List<String> = listOfNotNull(
        preferences.getString(UpdateDownloadWorker.KEY_PRIMARY_URL, null),
        preferences.getString(UpdateDownloadWorker.KEY_BACKUP_URL, null)
    ).filter(String::isNotBlank).distinct()

    private fun savedOutputFile(): File? = preferences
        .getString(UpdateDownloadWorker.KEY_DOWNLOAD_FILE, null)
        ?.takeIf(String::isNotBlank)
        ?.let(::File)

    private fun savedWorkId(): UUID? = preferences
        .getString(UpdateDownloadWorker.KEY_WORK_ID, null)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun clearReadyState() {
        preferences.edit()
            .remove(UpdateDownloadWorker.KEY_SHA256)
            .remove(UpdateDownloadWorker.KEY_VERSION_NAME)
            .remove(UpdateDownloadWorker.KEY_PRIMARY_URL)
            .remove(UpdateDownloadWorker.KEY_BACKUP_URL)
            .remove(UpdateDownloadWorker.KEY_DOWNLOAD_FILE)
            .remove(UpdateDownloadWorker.KEY_DOWNLOAD_READY)
            .remove(UpdateDownloadWorker.KEY_WORK_ID)
            .remove(UpdateDownloadWorker.KEY_LAST_ERROR)
            .apply()
    }

    private fun verifyFile(file: File, expectedSha: String): Boolean {
        if (!file.isFile || expectedSha.length != SHA256_LENGTH) return false
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha, ignoreCase = true)
    }

    private fun showToast(message: String, duration: Int) {
        activity.runOnUiThread { Toast.makeText(activity, message, duration).show() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val SHA256_LENGTH = 64
        const val NOTIFICATION_PERMISSION_REQUEST = 9506
    }
}
