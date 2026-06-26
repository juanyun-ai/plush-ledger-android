package com.plushledger.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.plushledger.sync.AppVersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class AppUpdateManager(private val activity: FragmentActivity) {
    private val preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadManager = activity.getSystemService(DownloadManager::class.java)
    private var receiverRegistered = false
    private var pollingJob: Job? = null
    private var verifying = false

    var uiState by mutableStateOf(UpdateDownloadUiState())
        private set

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, INVALID_DOWNLOAD_ID)
            if (completedId == savedDownloadId()) inspectDownload(completedId)
        }
    }

    fun register() {
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                activity,
                completionReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        }
        cleanupRetiredWorker()
        resumePendingDownload()
    }

    fun unregister() {
        pollingJob?.cancel()
        pollingJob = null
        if (receiverRegistered) {
            runCatching { activity.unregisterReceiver(completionReceiver) }
            receiverRegistered = false
        }
    }

    fun download(info: AppVersionInfo) {
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

        cancelSystemDownload(deleteFile = true)
        preferences.edit()
            .putString(KEY_PRIMARY_URL, sources.first())
            .putString(KEY_BACKUP_URL, sources.getOrNull(1))
            .putString(KEY_SHA256, info.sha256.lowercase())
            .putString(KEY_VERSION_NAME, info.versionName)
            .putString(KEY_DOWNLOAD_FILE, updateFile(info.versionName).absolutePath)
            .putLong(KEY_EXPECTED_SIZE, info.fileSizeBytes)
            .putInt(KEY_SOURCE_INDEX, 0)
            .putInt(KEY_SOURCE_ATTEMPT, 0)
            .putBoolean(KEY_DOWNLOAD_READY, false)
            .remove(KEY_LAST_ERROR)
            .remove(KEY_DOWNLOAD_ID)
            .apply()
        enqueueCurrentSource("正在交给系统下载服务")
    }

    fun cancelDownload() {
        cancelSystemDownload(deleteFile = false)
        uiState = uiState.copy(
            phase = UpdateDownloadPhase.CANCELLED,
            message = "下载已取消，可以随时重新开始"
        )
    }

    fun retryDownload() {
        if (savedSources().isEmpty() || savedOutputFile() == null) {
            uiState = uiState.copy(phase = UpdateDownloadPhase.FAILED, message = "更新信息已失效，请重新检查版本")
            return
        }
        cancelSystemDownload(deleteFile = true)
        preferences.edit()
            .putInt(KEY_SOURCE_INDEX, 0)
            .putInt(KEY_SOURCE_ATTEMPT, 0)
            .putBoolean(KEY_DOWNLOAD_READY, false)
            .remove(KEY_LAST_ERROR)
            .apply()
        enqueueCurrentSource("正在重新连接主线路")
    }

    fun openExternalDownload() {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_PAGE_URL)))
        }.onSuccess {
            showToast("已打开下载页，可在浏览器中选择下载线路", Toast.LENGTH_LONG)
        }.onFailure {
            showToast("无法打开系统浏览器，请检查默认浏览器设置", Toast.LENGTH_LONG)
        }
    }

    fun dismissDownloadStatus() {
        if (!uiState.isActive) uiState = UpdateDownloadUiState()
    }

    private fun enqueueCurrentSource(message: String) {
        val source = currentSource()
        val versionName = savedVersionName()
        val outputFile = savedOutputFile()
        if (source == null || versionName.isBlank() || outputFile == null) {
            fail("更新信息不完整，请重新检查版本")
            return
        }

        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        val request = DownloadManager.Request(Uri.parse(source))
            .setTitle("绒绒记账 v$versionName")
            .setDescription(if (savedSourceIndex() == 0) "正在下载更新" else "正在使用备用线路")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                outputFile.name
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
            .addRequestHeader("User-Agent", "RongRongLedger-Android-Updater")

        runCatching { downloadManager.enqueue(request) }
            .onSuccess { id ->
                preferences.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
                uiState = UpdateDownloadUiState(
                    phase = UpdateDownloadPhase.QUEUED,
                    versionName = versionName,
                    totalBytes = savedExpectedSize(),
                    message = message
                )
                startPolling(id)
            }
            .onFailure { retryOrSwitch("系统下载服务启动失败") }
    }

    private fun startPolling(downloadId: Long) {
        pollingJob?.cancel()
        pollingJob = activity.lifecycleScope.launch {
            while (isActive && savedDownloadId() == downloadId) {
                inspectDownload(downloadId)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun inspectDownload(downloadId: Long) {
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
            if (!cursor.moveToFirst()) {
                retryOrSwitch("系统下载任务已失效")
                return
            }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                .coerceAtLeast(0L)
            val reportedTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val total = reportedTotal.takeIf { it > 0L } ?: savedExpectedSize()
            val progress = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else -1
            when (status) {
                DownloadManager.STATUS_PENDING -> updateProgress(
                    UpdateDownloadPhase.QUEUED,
                    downloaded,
                    total,
                    progress,
                    "等待系统建立下载连接"
                )
                DownloadManager.STATUS_RUNNING -> updateProgress(
                    UpdateDownloadPhase.DOWNLOADING,
                    downloaded,
                    total,
                    progress,
                    if (savedSourceIndex() == 0) "正在接收安装包" else "正在通过备用线路接收安装包"
                )
                DownloadManager.STATUS_PAUSED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    updateProgress(
                        UpdateDownloadPhase.QUEUED,
                        downloaded,
                        total,
                        progress,
                        pausedMessage(reason)
                    )
                }
                DownloadManager.STATUS_SUCCESSFUL -> verifyAndInstall(downloadId, downloaded, total)
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    retryOrSwitch(downloadFailureMessage(reason))
                }
            }
        } ?: retryOrSwitch("无法读取系统下载状态")
    }

    private fun updateProgress(
        phase: UpdateDownloadPhase,
        downloaded: Long,
        total: Long,
        progress: Int,
        message: String
    ) {
        uiState = UpdateDownloadUiState(
            phase = phase,
            versionName = savedVersionName(),
            progress = progress,
            downloadedBytes = downloaded,
            totalBytes = total,
            message = message
        )
    }

    private fun verifyAndInstall(downloadId: Long, downloaded: Long, total: Long) {
        if (verifying) return
        verifying = true
        pollingJob?.cancel()
        uiState = UpdateDownloadUiState(
            phase = UpdateDownloadPhase.VERIFYING,
            versionName = savedVersionName(),
            progress = 100,
            downloadedBytes = downloaded,
            totalBytes = total,
            message = "下载完成，正在校验安装包"
        )
        activity.lifecycleScope.launch {
            val file = savedOutputFile()
            val expectedSha = preferences.getString(KEY_SHA256, "").orEmpty()
            val valid = withContext(Dispatchers.IO) { file != null && verifyFile(file, expectedSha) }
            verifying = false
            if (valid && file != null) {
                preferences.edit()
                    .putBoolean(KEY_DOWNLOAD_READY, true)
                    .remove(KEY_LAST_ERROR)
                    .apply()
                openInstaller(file)
            } else {
                downloadManager.remove(downloadId)
                retryOrSwitch("安装包完整性校验失败")
            }
        }
    }

    private fun retryOrSwitch(message: String) {
        pollingJob?.cancel()
        pollingJob = null
        savedDownloadId().takeIf { it >= 0L }?.let { downloadManager.remove(it) }
        preferences.edit().remove(KEY_DOWNLOAD_ID).apply()

        val sourceIndex = savedSourceIndex()
        val attempt = preferences.getInt(KEY_SOURCE_ATTEMPT, 0)
        when {
            attempt + 1 < MAX_ATTEMPTS_PER_SOURCE -> {
                preferences.edit().putInt(KEY_SOURCE_ATTEMPT, attempt + 1).apply()
                enqueueCurrentSource("当前线路中断，正在自动重试")
            }
            sourceAt(sourceIndex + 1) != null -> {
                preferences.edit()
                    .putInt(KEY_SOURCE_INDEX, sourceIndex + 1)
                    .putInt(KEY_SOURCE_ATTEMPT, 0)
                    .apply()
                enqueueCurrentSource("主线路不可用，正在切换备用线路")
            }
            else -> fail("$message。可以重新下载或改用系统浏览器")
        }
    }

    private fun resumePendingDownload() {
        if (preferences.getBoolean(KEY_DOWNLOAD_READY, false)) {
            val file = savedOutputFile()
            val expectedSha = preferences.getString(KEY_SHA256, "").orEmpty()
            activity.lifecycleScope.launch {
                val valid = withContext(Dispatchers.IO) { file != null && verifyFile(file, expectedSha) }
                if (valid && file != null) openInstaller(file) else preferences.edit().putBoolean(KEY_DOWNLOAD_READY, false).apply()
            }
            return
        }

        val downloadId = savedDownloadId()
        if (downloadId >= 0L) {
            inspectDownload(downloadId)
            startPolling(downloadId)
        }
    }

    private fun openInstaller(outputFile: File) {
        if (!outputFile.isFile) {
            fail("安装包文件不存在，请重新下载")
            return
        }
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
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onSuccess {
            uiState = UpdateDownloadUiState()
            clearPendingState(keepFile = true)
        }.onFailure {
            fail("系统安装器无法打开，请在下载目录中手动打开安装包")
        }
    }

    private fun fail(message: String) {
        preferences.edit().putString(KEY_LAST_ERROR, message).apply()
        uiState = UpdateDownloadUiState(
            phase = UpdateDownloadPhase.FAILED,
            versionName = savedVersionName(),
            downloadedBytes = savedOutputFile()?.length() ?: 0L,
            totalBytes = savedExpectedSize(),
            message = message
        )
    }

    private fun cancelSystemDownload(deleteFile: Boolean) {
        pollingJob?.cancel()
        pollingJob = null
        savedDownloadId().takeIf { it >= 0L }?.let { downloadManager.remove(it) }
        preferences.edit().remove(KEY_DOWNLOAD_ID).apply()
        if (deleteFile) savedOutputFile()?.delete()
    }

    private fun cleanupRetiredWorker() {
        WorkManager.getInstance(activity.applicationContext).cancelUniqueWork(RETIRED_WORK_NAME)
        preferences.edit().remove(KEY_RETIRED_WORK_ID).apply()
    }

    private fun updateFile(versionName: String): File {
        val directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(activity.filesDir, "updates")
        directory.mkdirs()
        return File(directory, "rongrong-ledger-$versionName.apk")
    }

    private fun savedSources(): List<String> = listOfNotNull(
        preferences.getString(KEY_PRIMARY_URL, null),
        preferences.getString(KEY_BACKUP_URL, null)
    ).filter(String::isNotBlank).distinct()

    private fun sourceAt(index: Int): String? = savedSources().getOrNull(index)

    private fun currentSource(): String? = sourceAt(savedSourceIndex())

    private fun savedSourceIndex(): Int = preferences.getInt(KEY_SOURCE_INDEX, 0)

    private fun savedVersionName(): String = preferences.getString(KEY_VERSION_NAME, "").orEmpty()

    private fun savedExpectedSize(): Long = preferences.getLong(KEY_EXPECTED_SIZE, -1L)

    private fun savedDownloadId(): Long = preferences.getLong(KEY_DOWNLOAD_ID, INVALID_DOWNLOAD_ID)

    private fun savedOutputFile(): File? = preferences
        .getString(KEY_DOWNLOAD_FILE, null)
        ?.takeIf(String::isNotBlank)
        ?.let(::File)

    private fun clearPendingState(keepFile: Boolean) {
        val file = savedOutputFile()
        preferences.edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_SHA256)
            .remove(KEY_VERSION_NAME)
            .remove(KEY_PRIMARY_URL)
            .remove(KEY_BACKUP_URL)
            .remove(KEY_DOWNLOAD_FILE)
            .remove(KEY_EXPECTED_SIZE)
            .remove(KEY_SOURCE_INDEX)
            .remove(KEY_SOURCE_ATTEMPT)
            .remove(KEY_DOWNLOAD_READY)
            .remove(KEY_LAST_ERROR)
            .apply()
        if (!keepFile) file?.delete()
    }

    private fun verifyFile(file: File, expectedSha: String): Boolean {
        if (!file.isFile || expectedSha.length != SHA256_LENGTH) return false
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha, ignoreCase = true)
    }

    private fun pausedMessage(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待网络连接"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待 Wi-Fi，已允许流量下载时请检查系统限制"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "网络波动，系统正在自动重试"
        else -> "系统暂时暂停下载，正在等待恢复"
    }

    private fun downloadFailureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "设备存储空间不足"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "旧安装包未能替换"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下载地址跳转异常"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "更新服务器连接异常"
        DownloadManager.ERROR_CANNOT_RESUME -> "下载中断且无法续传"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "下载存储位置不可用"
        else -> "系统下载失败（错误 $reason）"
    }

    private fun showToast(message: String, duration: Int) {
        activity.runOnUiThread { Toast.makeText(activity, message, duration).show() }
    }

    private companion object {
        const val PREFS_NAME = "app_updates"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val SHA256_LENGTH = 64
        const val MAX_ATTEMPTS_PER_SOURCE = 2
        const val POLL_INTERVAL_MS = 750L
        const val INVALID_DOWNLOAD_ID = -1L
        const val RETIRED_WORK_NAME = "app-update-download"
        const val DOWNLOAD_PAGE_URL = "https://juanyun-ai.github.io/plush-ledger-android/"
        const val KEY_RETIRED_WORK_ID = "work_id"
        const val KEY_DOWNLOAD_ID = "system_download_id"
        const val KEY_PRIMARY_URL = "primary_url"
        const val KEY_BACKUP_URL = "backup_url"
        const val KEY_SHA256 = "sha256"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_DOWNLOAD_FILE = "download_file"
        const val KEY_EXPECTED_SIZE = "expected_size"
        const val KEY_SOURCE_INDEX = "source_index"
        const val KEY_SOURCE_ATTEMPT = "source_attempt"
        const val KEY_DOWNLOAD_READY = "download_ready"
        const val KEY_LAST_ERROR = "last_error"
    }
}
