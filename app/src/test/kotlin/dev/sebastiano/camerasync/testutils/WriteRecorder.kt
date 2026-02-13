package dev.sebastiano.camerasync.testutils

import com.juul.kable.Characteristic
import com.juul.kable.WriteType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)

/**
 * Records BLE writes in tests for later assertion.
 *
 * Use with a mocked [Peripheral][com.juul.kable.Peripheral]: pass a [WriteRecorder] and in the
 * mock's `coEvery { write(any(), any(), any()) } answers { ... }` call [record] with
 * [firstArg][io.mockk.MockKMatcherScope.firstArg], [secondArg], [thirdArg].
 *
 * Supports both 2-arg and 3-arg [record] for tests that do not care about [WriteType].
 */
class WriteRecorder {

    private data class Entry(
        val characteristicUuid: Uuid,
        val data: ByteArray,
        val writeType: WriteType?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false
            if (characteristicUuid != other.characteristicUuid) return false
            if (!data.contentEquals(other.data)) return false
            if (writeType != other.writeType) return false
            return true
        }

        override fun hashCode(): Int {
            var result = characteristicUuid.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + (writeType?.hashCode() ?: 0)
            return result
        }
    }

    private val writes = mutableListOf<Entry>()

    /** Records a write. Use when the mock captures (Characteristic, ByteArray, WriteType). */
    fun record(characteristic: Characteristic, data: ByteArray, writeType: WriteType?) {
        writes.add(
            Entry(
                characteristicUuid = characteristic.characteristicUuid,
                data = data.copyOf(),
                writeType = writeType,
            )
        )
    }

    /**
     * Records a write without WriteType. Use when the mock only captures (Characteristic,
     * ByteArray).
     */
    fun record(characteristic: Characteristic, data: ByteArray) {
        record(characteristic, data, null)
    }

    /** All data payloads written to the given characteristic, in order. */
    fun dataFor(characteristicUuid: Uuid): List<ByteArray> =
        writes.filter { it.characteristicUuid == characteristicUuid }.map { it.data }

    /**
     * All write types for the given characteristic (only entries that had a non-null type), in
     * order.
     */
    fun writeTypesFor(characteristicUuid: Uuid): List<WriteType> =
        writes
            .filter { it.characteristicUuid == characteristicUuid && it.writeType != null }
            .map { it.writeType!! }

    fun hasWriteTo(characteristicUuid: Uuid): Boolean =
        writes.any { it.characteristicUuid == characteristicUuid }

    fun hasWriteToWithData(characteristicUuid: Uuid, data: ByteArray): Boolean =
        writes.any { it.characteristicUuid == characteristicUuid && it.data.contentEquals(data) }

    /** Last data written to the given characteristic, or null. */
    fun dataWrittenTo(characteristicUuid: Uuid): ByteArray? =
        writes.lastOrNull { it.characteristicUuid == characteristicUuid }?.data

    fun countWritesWithData(characteristicUuid: Uuid, data: ByteArray): Int =
        writes.count { it.characteristicUuid == characteristicUuid && it.data.contentEquals(data) }

    fun getAllWrites(): List<Pair<Uuid, ByteArray>> =
        writes.map { it.characteristicUuid to it.data }
}
