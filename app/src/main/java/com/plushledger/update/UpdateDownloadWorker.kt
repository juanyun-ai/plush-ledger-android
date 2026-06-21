package com.plushledger.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.plushledger.MainActivity
import com.plushledger.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class UpdateDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createNotificationChannel()
        setForeground(downloadForegroundInfo(progress = null, "准备下载更新"))

        val versionName = inputData.getString(KEY_VERSION_NAME).orEmpty()
        val expectedSha = inputData.getString(KEY_SHA256).orEmpty().lowercase()
        val outputPath = inputData.getString(KEY_OUTPUT_PATH).orEmpty()
        val sources = listOfNotNull(
            inputData.getString(KEY_PRIMARY_URL),
            inputData.getString(KEY_BACKUP_URL)
        ).filter(String::isNotBlank)

        if (versionName.isBlank() || expectedSha.length != SHA256_LENGTH || outputPath.isBlank() || sources.isEmpty()) {
            return@withContext failed("更新信息不完整")
        }

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        if (outputFile.isFile && verifyFile(outputFile, expectedSha)) {
            markReady(outputFile)
            showCompletedNotification(versionName)
            return@withContext Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))
        }

        var lastError: IOException? = null
        sources.forEachIndexed { sourceIndex, source ->
            val partialFile = File(outputFile.parentFile, "${outputFile.name}.source$sourceIndex.part")
            repeat(MAX_ATTEMPTS_PER_SOURCE) { attempt ->
                try {
                    downloadToPartialFile(
                        url = source,
                        partialFile = partialFile,
                        status = when {
                            sourceIndex > 0 -> "正在切换备用线路"
                            attempt > 0 -> "下载中断，正在续传"
                            else -> "正在下载 v$versionName"
                        }
                    )
                    if (!verifyFile(partialFile, expectedSha)) {
                        partialFile.delete()
                        throw IOException("安装包安全校验失败")
                    }
                    if (outputFile.exists() && !outputFile.delete()) {
                        throw IOException("无法替换旧安装包")
                    }
                    if (!partialFile.renameTo(outputFile)) {
                        partialFile.copyTo(outputFile, overwrite = true)
                        partialFile.delete()
                    }
                    markReady(outputFile)
                    showCompletedNotification(versionName)
                    return@withContext Result.success(workDataOf(KEY_OUTPUT_PATH to outputFile.absolutePath))
                } catch (error: IOException) {
                    lastError = error
                }
            }
        }

        failed(lastError?.message ?: "所有下载线路均不可用")
    }

    private suspend fun downloadToPartialFile(url: String, partialFile: File, status: String) {
        partialFile.parentFile?.mkdirs()
        val existingBytes = partialFile.takeIf(File::exists)?.length() ?: 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
            setRequestProperty("User-Agent", "RongRongLedger-Android-Updater")
            if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode == HTTP_RANGE_NOT_SATISFIABLE && partialFile.isFile) return
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("服务器返回 HTTP $responseCode")
            }

            val appending = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            val totalBytes = totalDownloadBytes(
                contentRange = connection.getHeaderField("Content-Range"),
                contentLength = connection.contentLengthLong,
                existingBytes = existingBytes,
                appending = appending
            )
            var downloadedBytes = if (appending) existingBytes else 0L
            var lastProgress = -1

            connection.inputStream.use { input ->
                FileOutputStream(partialFile, appending).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (isStopped) throw IOException("下载已取消")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        val progress = if (totalBytes > 0L) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        if (progress < 0 || progress >= lastProgress + PROGRESS_STEP) {
                            lastProgress = progress
                            setProgress(workDataOf(KEY_PROGRESS to progress, KEY_DOWNLOADED_BYTES to downloadedBytes))
                            setForeground(downloadForegroundInfo(progress.takeIf { it >= 0 }, status))
                        }
                    }
                    output.fd.sync()
                }
            }

            if (totalBytes > 0L && partialFile.length() != totalBytes) {
                throw IOException("下载未完成（${partialFile.length()}/$totalBytes 字节）")
            }
        } catch (error: IOException) {
            throw IOException(error.message ?: "读取安装包时连接中断", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadForegroundInfo(progress: Int?, status: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("绒绒记账正在更新")
            .setContentText(if (progress == null) status else "$status · $progress%")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress ?: 0, progress == null)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun showCompletedNotification(versionName: String) {
        if (!canPostNotifications()) return
        val launchIntent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_INSTALL_UPDATE, true)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            versionName.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationManager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("更新包下载完成")
                .setContentText("点击安装绒绒记账 v$versionName")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun failed(message: String): Result {
        preferences.edit()
            .putBoolean(KEY_DOWNLOAD_READY, false)
            .putString(KEY_LAST_ERROR, message)
            .apply()
        if (canPostNotifications()) {
            notificationManager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("更新包下载失败")
                    .setContentText(message.take(80))
                    .setAutoCancel(true)
                    .build()
            )
        }
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun markReady(outputFile: File) {
        preferences.edit()
            .putBoolean(KEY_DOWNLOAD_READY, true)
            .putString(KEY_DOWNLOAD_FILE, outputFile.absolutePath)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "应用更新", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示安装包下载进度和完成状态"
            }
        )
    }

    private fun verifyFile(file: File, expectedSha: String): Boolean {
        if (!file.isFile || expectedSha.length != SHA256_LENGTH) return false
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha, ignoreCase = true)
    }

    companion object {
        const val PREFS_NAME = "app_updates"
        const val UNIQUE_WORK_NAME = "app-update-download"
        const val EXTRA_INSTALL_UPDATE = "install_downloaded_update"
        const val KEY_PRIMARY_URL = "primary_url"
        const val KEY_BACKUP_URL = "backup_url"
        const val KEY_SHA256 = "sha256"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_DOWNLOAD_FILE = "download_file"
        const val KEY_DOWNLOAD_READY = "download_ready"
        const val KEY_WORK_ID = "work_id"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_ERROR = "error"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"

        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 9506
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 90_000
        private const val MAX_ATTEMPTS_PER_SOURCE = 2
        private const val PROGRESS_STEP = 2
        private const val SHA256_LENGTH = 64
        private const val BUFFER_SIZE = 64 * 1024
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
