package dev.sebastiano.camerasync.di

import dev.sebastiano.camerasync.devicesync.IntentFactory
import dev.sebastiano.camerasync.devicesync.NotificationBuilder
import dev.sebastiano.camerasync.devicesync.PendingIntentFactory
import dev.sebastiano.camerasync.fakes.FakeIntentFactory
import dev.sebastiano.camerasync.fakes.FakeNotificationBuilder
import dev.sebastiano.camerasync.fakes.FakePendingIntentFactory
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

/** Metro dependency graph for test dependencies using fake implementations. */
@DependencyGraph
interface TestGraph {
    val notificationBuilder: NotificationBuilder
    val intentFactory: IntentFactory
    val pendingIntentFactory: PendingIntentFactory

    @Provides fun provideNotificationBuilder(): NotificationBuilder = FakeNotificationBuilder()

    @Provides fun provideIntentFactory(): IntentFactory = FakeIntentFactory()

    @Provides fun providePendingIntentFactory(): PendingIntentFactory = FakePendingIntentFactory()
}
