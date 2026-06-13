package com.plushledger.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.plushledger.sync.AppVersionInfo
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class AppUpdateManager(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val checking = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val expectedId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
            if (completedId != expectedId) return
            inspectDownload(completedId)
        }
    }

    fun register() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        resumePendingDownload()
    }

    fun unregister() {
        runCatching { activity.unregisterReceiver(receiver) }
    }

    fun download(info: AppVersionInfo) {
        val downloadsDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val fileName = "plush-ledger-${info.versionName}.apk"
        downloadsDir?.let { File(it, fileName).delete() }

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("绒绒记账 ${info.versionName}")
            .setDescription("正在下载应用更新")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val manager = activity.getSystemService(DownloadManager::class.java)
        runCatching { manager.enqueue(request) }
            .onSuccess { id ->
                preferences.edit()
                    .putLong(KEY_DOWNLOAD_ID, id)
                    .putString(KEY_SHA256, info.sha256.lowercase())
                    .putString(KEY_VERSION_NAME, info.versionName)
                    .putBoolean(KEY_WAITING_INSTALL_PERMISSION, false)
                    .putBoolean(KEY_PERMISSION_REQUESTED, false)
                    .apply()
                Toast.makeText(activity, "v${info.versionName} 已开始下载", Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                Toast.makeText(activity, "无法开始下载，请检查网络后重试", Toast.LENGTH_LONG).show()
            }
    }

    private fun resumePendingDownload() {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id < 0) return
        if (preferences.getBoolean(KEY_WAITING_INSTALL_PERMISSION, false)) {
            if (activity.packageManager.canRequestPackageInstalls()) openInstaller(id)
            return
        }
        inspectDownload(id)
    }

    private fun inspectDownload(id: Long) {
        if (!checking.compareAndSet(false, true)) return
        val manager = activity.getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) {
                checking.set(false)
                return
            }
            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> verifyAndInstall(id)
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    clearPending()
                    checking.set(false)
                    Toast.makeText(activity, downloadFailureMessage(reason), Toast.LENGTH_LONG).show()
                }
                else -> checking.set(false)
            }
        } ?: checking.set(false)
    }

    private fun verifyAndInstall(id: Long) {
        val expectedSha = preferences.getString(KEY_SHA256, null)
        if (expectedSha == null) {
            checking.set(false)
            return
        }
        Thread {
            val valid = verifyDownload(id, expectedSha)
            activity.runOnUiThread {
                checking.set(false)
                if (valid) {
                    openInstaller(id)
                } else {
                    clearPending()
                    Toast.makeText(activity, "更新包校验失败，已拒绝安装", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun verifyDownload(id: Long, expectedSha: String): Boolean {
        val manager = activity.getSystemService(DownloadManager::class.java)
        val uri = manager.getUriForDownloadedFile(id) ?: return false
        val digest = MessageDigest.getInstance("SHA-256")
        activity.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: return false
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha, ignoreCase = true)
    }

    private fun openInstaller(id: Long) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            preferences.edit().putBoolean(KEY_WAITING_INSTALL_PERMISSION, true).apply()
            if (!preferences.getBoolean(KEY_PERMISSION_REQUESTED, false)) {
                preferences.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                )
                Toast.makeText(activity, "下载完成，请允许安装未知应用以继续更新", Toast.LENGTH_LONG).show()
            }
            return
        }
        val manager = activity.getSystemService(DownloadManager::class.java)
        val uri = manager.getUriForDownloadedFile(id) ?: return
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        clearPending()
    }

    private fun clearPending() {
        preferences.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_SHA256).remove(KEY_VERSION_NAME)
            .remove(KEY_WAITING_INSTALL_PERMISSION).remove(KEY_PERMISSION_REQUESTED).apply()
    }

    private fun downloadFailureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "下载失败：设备存储空间不足"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "下载失败：旧安装包未能替换，请重试"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下载失败：下载地址跳转异常"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "下载失败：服务器暂时无法提供安装包"
        DownloadManager.ERROR_CANNOT_RESUME -> "下载中断且无法续传，请重新下载"
        else -> "下载失败，请检查网络后重试（错误 $reason）"
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_SHA256 = "sha256"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_WAITING_INSTALL_PERMISSION = "waiting_install_permission"
        const val KEY_PERMISSION_REQUESTED = "permission_requested"
    }
}
