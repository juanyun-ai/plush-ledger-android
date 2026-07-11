package com.plushledger.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SystemDownloadManagerInstrumentedTest {
    @Test
    fun downloadsPublishedApkThroughAndroidSystemService() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(DownloadManager::class.java)
        val directory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        val outputFile = File(directory, TEST_FILE_NAME)
        outputFile.delete()

        val request = DownloadManager.Request(Uri.parse(PUBLISHED_APK_URL))
            .setTitle("绒绒记账下载测试")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, TEST_FILE_NAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
            .addRequestHeader("User-Agent", "RongRongLedger-Android-Updater")

        val id = manager.enqueue(request)
        try {
            val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(4)
            var status = DownloadManager.STATUS_PENDING
            var reason = 0
            while (System.currentTimeMillis() < deadline) {
                manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    assertTrue("系统下载任务丢失", cursor.moveToFirst())
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) break
                Thread.sleep(750)
            }

            assertEquals("系统下载失败，原因码 $reason", DownloadManager.STATUS_SUCCESSFUL, status)
            assertEquals(PUBLISHED_APK_SIZE, outputFile.length())
            assertEquals(PUBLISHED_APK_SHA256, sha256(outputFile))
            assertTrue(FileInputStream(outputFile).use { it.read() == 'P'.code && it.read() == 'K'.code })
        } finally {
            manager.remove(id)
            outputFile.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val TEST_FILE_NAME = "system-download-qa.apk"
        const val PUBLISHED_APK_URL =
            "https://raw.githubusercontent.com/juanyun-ai/plush-ledger-android/main/docs/downloads/rongrong-ledger-1.4.0.apk"
        const val PUBLISHED_APK_SHA256 = "aa38ac03521596cb10f933a76857e58d68f8fdc72befc47d0f7a75ba3c4b649a"
        const val PUBLISHED_APK_SIZE = 15_384_774L
    }
}
