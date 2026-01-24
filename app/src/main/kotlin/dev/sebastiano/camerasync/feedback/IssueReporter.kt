package dev.sebastiano.camerasync.feedback

import dev.sebastiano.camerasync.domain.repository.CameraConnection

interface IssueReporter {
    suspend fun sendIssueReport(connection: CameraConnection? = null, extraInfo: String? = null)
}
