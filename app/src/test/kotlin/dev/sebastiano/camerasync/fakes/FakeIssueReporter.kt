package dev.sebastiano.camerasync.fakes

import dev.sebastiano.camerasync.domain.repository.CameraConnection
import dev.sebastiano.camerasync.feedback.IssueReporter

class FakeIssueReporter : IssueReporter {

    var sendIssueReportCalled = false
        private set

    var lastConnection: CameraConnection? = null
        private set

    var lastExtraInfo: String? = null
        private set

    override suspend fun sendIssueReport(connection: CameraConnection?, extraInfo: String?) {
        sendIssueReportCalled = true
        lastConnection = connection
        lastExtraInfo = extraInfo
    }
}
