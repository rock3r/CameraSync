package dev.sebastiano.camerasync.testutils

import com.juul.kable.Characteristic
import com.juul.kable.WriteType
import kotlin.OptIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Records BLE writes for later assertion in tests. Avoids MockK's `match` limitations.
 *
 * Tracks writes by characteristic UUID, data, and write type for comprehensive test verification.
 */
@OptIn(ExperimentalUuidApi::class)
class WriteRecorder {
    data class Record(val charUuid: Uuid, val data: ByteArray, val writeType: WriteType) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Record

            if (charUuid != other.charUuid) return false
            if (!data.contentEquals(other.data)) return false
            if (writeType != other.writeType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = charUuid.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + writeType.hashCode()
            return result
        }
    }

    private val writes = mutableListOf<Record>()

    fun record(char: Characteristic, data: ByteArray, writeType: WriteType) {
        writes.add(Record(char.characteristicUuid, data.copyOf(), writeType))
    }

    fun dataFor(charUuid: Uuid): List<ByteArray> =
        writes.filter { it.charUuid == charUuid }.map { it.data }

    fun writeTypesFor(charUuid: Uuid): List<WriteType> =
        writes.filter { it.charUuid == charUuid }.map { it.writeType }
}
