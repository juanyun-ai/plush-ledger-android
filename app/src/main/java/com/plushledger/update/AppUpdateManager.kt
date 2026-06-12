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
import java.security.MessageDigest

class AppUpdateManager(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val expectedId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
            if (completedId != expectedId) return
            val expectedSha = preferences.getString(KEY_SHA256, null) ?: return
            Thread {
                if (verifyDownload(completedId, expectedSha)) {
                    activity.runOnUiThread { openInstaller(completedId) }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "更新包校验失败，已拒绝安装", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    fun register() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun unregister() {
        runCatching { activity.unregisterReceiver(receiver) }
    }

    fun download(info: AppVersionInfo) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            Toast.makeText(activity, "请先允许绒绒记账安装更新包，再重新点击下载", Toast.LENGTH_LONG).show()
            return
        }

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("绒绒记账 ${info.versionName}")
            .setDescription("正在下载应用更新")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                "plush-ledger-${info.versionName}.apk"
            )

        val manager = activity.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(request)
        preferences.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_SHA256, info.sha256.lowercase()).apply()
        Toast.makeText(activity, "更新包已开始下载", Toast.LENGTH_SHORT).show()
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
        val manager = activity.getSystemService(DownloadManager::class.java)
        val uri = manager.getUriForDownloadedFile(id) ?: return
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_SHA256 = "sha256"
    }
}
