package dev.sebastiano.camerasync.fakes

import dev.sebastiano.camerasync.widget.WidgetUpdateHelper

class FakeWidgetUpdateHelper : WidgetUpdateHelper {
    var updateWidgetsCalled = false
    var updateWidgetsCallCount = 0

    override suspend fun updateWidgets() {
        updateWidgetsCalled = true
        updateWidgetsCallCount++
    }
}
