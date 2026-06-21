package com.plushledger.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.plushledger.sync.AppVersionInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads updates inside the app instead of delegating the stream to DownloadManager.
 * This lets us resume partial APKs and reliably switch between the two release sources.
 */
class AppUpdateManager(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val downloadInProgress = AtomicBoolean(false)
    private val downloadExecutor = Executors.newSingleThreadExecutor()

    fun register() {
        resumePendingDownload()
    }

    fun unregister() = Unit

    fun download(info: AppVersionInfo) {
        val sources = listOfNotNull(info.apkUrl, info.backupApkUrl)
            .map(String::trim)
            .filter { it.startsWith("https://") }
            .distinct()
        if (sources.isEmpty()) {
            showToast("更新地址无效，请稍后重试", Toast.LENGTH_LONG)
            return
        }

        val outputFile = updateFile(info.versionName)
        preferences.edit()
            .remove(KEY_LEGACY_DOWNLOAD_ID)
            .putString(KEY_PRIMARY_URL, sources.first())
            .putString(KEY_BACKUP_URL, sources.getOrNull(1))
            .putString(KEY_SHA256, info.sha256.lowercase())
            .putString(KEY_VERSION_NAME, info.versionName)
            .putString(KEY_DOWNLOAD_FILE, outputFile.absolutePath)
            .putBoolean(KEY_WAITING_INSTALL_PERMISSION, false)
            .putBoolean(KEY_PERMISSION_REQUESTED, false)
            .apply()
        startDownload(showStartedMessage = true)
    }

    private fun resumePendingDownload() {
        val outputFile = savedOutputFile() ?: return
        val expectedSha = preferences.getString(KEY_SHA256, null) ?: return
        if (outputFile.isFile && verifyFile(outputFile, expectedSha)) {
            openInstaller(outputFile)
        } else if (sourceAt(0) != null) {
            startDownload(showStartedMessage = false)
        }
    }

    private fun startDownload(showStartedMessage: Boolean) {
        if (!downloadInProgress.compareAndSet(false, true)) {
            if (showStartedMessage) showToast("更新包正在下载中", Toast.LENGTH_SHORT)
            return
        }
        val versionName = preferences.getString(KEY_VERSION_NAME, null)
        if (showStartedMessage && versionName != null) {
            showToast("v$versionName 已开始下载", Toast.LENGTH_SHORT)
        }

        downloadExecutor.execute {
            val result = runCatching { downloadFromAvailableSources() }
            activity.runOnUiThread {
                downloadInProgress.set(false)
                result.onSuccess { outputFile ->
                    preferences.edit().putBoolean(KEY_WAITING_INSTALL_PERMISSION, false).apply()
                    openInstaller(outputFile)
                }.onFailure { error ->
                    showToast(
                        "下载失败：${error.message ?: "请检查网络后重试"}",
                        Toast.LENGTH_LONG
                    )
                }
            }
        }
    }

    private fun downloadFromAvailableSources(): File {
        val expectedSha = preferences.getString(KEY_SHA256, null)
            ?.takeIf { it.length == SHA256_LENGTH }
            ?: throw IOException("更新包校验信息缺失")
        val outputFile = savedOutputFile() ?: throw IOException("无法创建更新文件")
        outputFile.parentFile?.mkdirs()

        var lastError: IOException? = null
        var sourceIndex = 0
        while (true) {
            val source = sourceAt(sourceIndex) ?: break
            val partialFile = File(outputFile.parentFile, "${outputFile.name}.source$sourceIndex.part")
            repeat(MAX_ATTEMPTS_PER_SOURCE) { attempt ->
                showToast(
                    when {
                        sourceIndex == 0 && attempt == 0 -> "正在下载更新包"
                        sourceIndex == 0 -> "下载中断，正在断点续传"
                        else -> "正在切换备用下载线路"
                    },
                    Toast.LENGTH_SHORT
                )
                try {
                    downloadToPartialFile(source, partialFile)
                    if (!verifyFile(partialFile, expectedSha)) {
                        partialFile.delete()
                        throw IOException("更新包校验失败")
                    }
                    if (outputFile.exists()) outputFile.delete()
                    if (!partialFile.renameTo(outputFile)) {
                        partialFile.copyTo(outputFile, overwrite = true)
                        partialFile.delete()
                    }
                    return outputFile
                } catch (error: IOException) {
                    lastError = error
                }
            }
            sourceIndex += 1
        }

        throw IOException(lastError?.message ?: "所有下载线路均不可用")
    }

    @Throws(IOException::class)
    private fun downloadToPartialFile(url: String, partialFile: File) {
        partialFile.parentFile?.mkdirs()
        val existingBytes = partialFile.takeIf(File::exists)?.length() ?: 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
            if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
        }

        try {
            val responseCode = connection.responseCode
            val append = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            if (responseCode !in HttpURLConnection.HTTP_OK..HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("服务器返回 HTTP $responseCode")
            }
            connection.inputStream.use { input ->
                FileOutputStream(partialFile, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        } catch (error: IOException) {
            throw IOException(error.message ?: "读取更新包时连接中断", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun openInstaller(outputFile: File) {
        if (!outputFile.isFile) return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            preferences.edit().putBoolean(KEY_WAITING_INSTALL_PERMISSION, true).apply()
            if (!preferences.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
                preferences.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
                showToast("下载完成，请允许安装未知应用以继续更新", Toast.LENGTH_LONG)
            }
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
        clearPending()
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

    private fun updateFile(versionName: String): File {
        val directory = requireNotNull(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        return File(directory, "rongrong-ledger-$versionName.apk")
    }

    private fun savedOutputFile(): File? = preferences.getString(KEY_DOWNLOAD_FILE, null)
        ?.takeIf(String::isNotBlank)
        ?.let(::File)

    private fun sourceAt(index: Int): String? = when (index) {
        0 -> preferences.getString(KEY_PRIMARY_URL, null)
        1 -> preferences.getString(KEY_BACKUP_URL, null)
        else -> null
    }?.takeIf(String::isNotBlank)

    private fun clearPending() {
        preferences.edit()
            .remove(KEY_LEGACY_DOWNLOAD_ID)
            .remove(KEY_SHA256)
            .remove(KEY_VERSION_NAME)
            .remove(KEY_PRIMARY_URL)
            .remove(KEY_BACKUP_URL)
            .remove(KEY_DOWNLOAD_FILE)
            .remove(KEY_WAITING_INSTALL_PERMISSION)
            .remove(KEY_PERMISSION_REQUESTED)
            .apply()
    }

    private fun showToast(message: String, duration: Int) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, duration).show()
        }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_ATTEMPTS_PER_SOURCE = 3
        const val SHA256_LENGTH = 64
        const val KEY_LEGACY_DOWNLOAD_ID = "download_id"
        const val KEY_SHA256 = "sha256"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_PRIMARY_URL = "primary_url"
        const val KEY_BACKUP_URL = "backup_url"
        const val KEY_DOWNLOAD_FILE = "download_file"
        const val KEY_WAITING_INSTALL_PERMISSION = "waiting_install_permission"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
    }
}
