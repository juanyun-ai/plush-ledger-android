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
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.fragment.app.FragmentActivity
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
    private val handledWorkIds = mutableSetOf<UUID>()

    fun register() {
        resumeReadyDownload()
        savedWorkId()?.let(::observeWork)
    }

    fun unregister() = Unit

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
                    UpdateDownloadWorker.KEY_SHA256 to info.sha256.lowercase(),
                    UpdateDownloadWorker.KEY_VERSION_NAME to info.versionName,
                    UpdateDownloadWorker.KEY_OUTPUT_PATH to outputFile.absolutePath
                )
            )
            .addTag(UpdateDownloadWorker.UNIQUE_WORK_NAME)
            .build()

        preferences.edit().putString(UpdateDownloadWorker.KEY_WORK_ID, request.id.toString()).apply()
        requestNotificationPermissionIfNeeded()
        handledWorkIds.remove(request.id)
        workManager.enqueueUniqueWork(
            UpdateDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        observeWork(request.id)
        showToast(
            if (canPostNotifications()) "已开始后台下载，可在系统通知中查看进度"
            else "已开始后台下载；允许通知后可查看实时进度",
            Toast.LENGTH_LONG
        )
    }

    private fun observeWork(id: UUID) {
        if (observedWorkId == id) return
        observedWorkId = id
        workManager.getWorkInfoByIdLiveData(id).observe(activity, Observer { info ->
            if (info == null || !info.state.isFinished || !handledWorkIds.add(id)) return@Observer
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> resumeReadyDownload()
                WorkInfo.State.FAILED -> {
                    val error = info.outputData.getString(UpdateDownloadWorker.KEY_ERROR)
                        ?: preferences.getString(UpdateDownloadWorker.KEY_LAST_ERROR, null)
                        ?: "请检查网络后重试"
                    showToast("下载失败：$error", Toast.LENGTH_LONG)
                }
                WorkInfo.State.CANCELLED -> showToast("更新下载已取消", Toast.LENGTH_SHORT)
                else -> Unit
            }
        })
    }

    private fun resumeReadyDownload() {
        if (!preferences.getBoolean(UpdateDownloadWorker.KEY_DOWNLOAD_READY, false)) return
        val outputFile = savedOutputFile() ?: return
        val expectedSha = preferences.getString(UpdateDownloadWorker.KEY_SHA256, null) ?: return
        if (!verifyFile(outputFile, expectedSha)) {
            preferences.edit().putBoolean(UpdateDownloadWorker.KEY_DOWNLOAD_READY, false).apply()
            showToast("更新包校验失败，请重新下载", Toast.LENGTH_LONG)
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
        clearReadyState()
    }

    private fun updateFile(versionName: String): File {
        val directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(activity.filesDir, "updates")
        directory.mkdirs()
        return File(directory, "rongrong-ledger-$versionName.apk")
    }

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
