package com.plushledger.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDownloadSupportTest {
    private val github = "https://github.com/example/app/releases/download/v1/app.apk"
    private val domestic = "https://project.supabase.co/storage/v1/object/public/releases/app.apk"

    @Test
    fun domesticSourceIsActuallyUsedFirstByDefault() {
        assertEquals(
            listOf(domestic, github),
            UpdateSourceSelector.order(listOf(github, domestic), "国内优先")
        )
    }

    @Test
    fun explicitGitHubPreferenceIsRespected() {
        assertEquals(
            listOf(github, domestic),
            UpdateSourceSelector.order(listOf(domestic, github), "GitHub 优先")
        )
    }

    @Test
    fun contentRangeProvidesCompleteFileSizeForResume() {
        assertEquals(
            27_724_785L,
            totalDownloadBytes("bytes 1024-2047/27724785", 1024L, 1024L, appending = true)
        )
    }
}
