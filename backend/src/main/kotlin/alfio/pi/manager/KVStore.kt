package alfio.pi.manager

import ch.digitalfondue.synckv.SyncKV
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
open class KVStore {

    private val store = SyncKV("alfio-pi-synckv", "alfio-pi-synckv")

    private val attendeeTable: SyncKV.SyncKVTable
    private val lastUpdatedTable: SyncKV.SyncKVTable

    init {
        attendeeTable = store.getTable("attendee")
        lastUpdatedTable = store.getTable("last_updated")
    }

    open fun putAttendeeData(event: String, identifier: String, payload: String) {
        attendeeTable.put(attendeeKey(event, identifier), payload.toByteArray(StandardCharsets.UTF_8))
    }

    open fun isAttendeeDataPresent(event: String, identifier: String): Boolean {
        return attendeeTable.get(attendeeKey(event, identifier)) != null
    }

    open fun getAttendeeData(event: String, identifier: String): String? {
        val res = attendeeTable.get(attendeeKey(event, identifier))
        return if (res != null) {
            String(res, StandardCharsets.UTF_8)
        } else {
            null
        }
    }

    open fun putLastUpdated(event: String, lastUpdated: Long) {
        lastUpdatedTable.put(event, lastUpdated.toString().toByteArray(StandardCharsets.UTF_8))
    }

    open fun getLatestUpdate(event: String): Long {
        val res = lastUpdatedTable.get(event)
        return if (res != null) {
            String(res, StandardCharsets.UTF_8).toLong()
        } else {
            -1
        }
    }
}

private fun attendeeKey(event: String, identifier: String): String {
    return event + "_" + identifier
}