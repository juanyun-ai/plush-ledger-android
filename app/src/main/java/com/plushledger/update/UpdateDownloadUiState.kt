package com.plushledger.update

enum class UpdateDownloadPhase {
    IDLE,
    QUEUED,
    DOWNLOADING,
    VERIFYING,
    FAILED,
    CANCELLED
}

data class UpdateDownloadUiState(
    val phase: UpdateDownloadPhase = UpdateDownloadPhase.IDLE,
    val versionName: String = "",
    val progress: Int = -1,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val message: String = ""
) {
    val isActive: Boolean
        get() = phase == UpdateDownloadPhase.QUEUED ||
            phase == UpdateDownloadPhase.DOWNLOADING ||
            phase == UpdateDownloadPhase.VERIFYING

    val isVisible: Boolean
        get() = phase != UpdateDownloadPhase.IDLE
}
