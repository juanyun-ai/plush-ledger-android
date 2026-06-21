package com.plushledger.update

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class UpdateDownloadWorkerInstrumentedTest {
    @Test
    fun downloadsAndVerifiesPublishedApk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        val outputFile = File(directory, "update-download-qa.apk")
        outputFile.delete()
        File(directory, "${outputFile.name}.source0.part").delete()

        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    UpdateDownloadWorker.KEY_PRIMARY_URL to PUBLISHED_APK_URL,
                    UpdateDownloadWorker.KEY_SHA256 to PUBLISHED_APK_SHA256,
                    UpdateDownloadWorker.KEY_VERSION_NAME to "0.9.5-qa",
                    UpdateDownloadWorker.KEY_OUTPUT_PATH to outputFile.absolutePath
                )
            )
            .build()

        val manager = WorkManager.getInstance(context)
        manager.enqueueUniqueWork("update-download-qa", ExistingWorkPolicy.REPLACE, request).result.get(30, TimeUnit.SECONDS)

        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(4)
        var info = manager.getWorkInfoById(request.id).get(30, TimeUnit.SECONDS)
        while (!info.state.isFinished && System.currentTimeMillis() < deadline) {
            Thread.sleep(1_000)
            info = manager.getWorkInfoById(request.id).get(30, TimeUnit.SECONDS)
        }

        assertEquals(info.outputData.getString(UpdateDownloadWorker.KEY_ERROR), WorkInfo.State.SUCCEEDED, info.state)
        assertEquals(PUBLISHED_APK_SIZE, outputFile.length())
        assertEquals(PUBLISHED_APK_SHA256, sha256(outputFile))
        assertTrue(FileInputStream(outputFile).use { it.read() == 'P'.code && it.read() == 'K'.code })
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
        const val PUBLISHED_APK_URL =
            "https://tjcijqvweivqgqfpoehf.supabase.co/storage/v1/object/public/app-releases/rongrong-ledger-0.9.5-debug.apk"
        const val PUBLISHED_APK_SHA256 = "b4b197839973404ce560d0fbd7ba181185d8a61bc2262f5beea709fdef61ec54"
        const val PUBLISHED_APK_SIZE = 27_724_785L
    }
}
