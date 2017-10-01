package alfio.pi.manager

import alfio.pi.model.CheckInStatus
import alfio.pi.model.ScanLog
import ch.digitalfondue.synckv.SyncKV
import com.google.gson.Gson
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.*
import kotlin.collections.ArrayList

@Component
open class KVStore(private val gson: Gson) {

    private val store = SyncKV("alfio-pi-synckv", "alfio-pi-synckv")

    private val attendeeTable: SyncKV.SyncKVTable
    private val lastUpdatedTable: SyncKV.SyncKVTable
    //
    private val scanLogTable: SyncKV.SyncKVTable
    private val scanLogRemoteResultSupport: SyncKV.SyncKVTable
    private val scanLogLocalResultSupport: SyncKV.SyncKVTable
    private val scanLogTicketUUIDSupport: SyncKV.SyncKVTable
    private val scanLogEventIdSupport: SyncKV.SyncKVTable

    init {
        attendeeTable = store.getTable("attendee")
        lastUpdatedTable = store.getTable("last_updated")

        //
        scanLogTable = store.getTable("scan_log")
        scanLogRemoteResultSupport = store.getTable("scan_log_remote_result_support")
        scanLogLocalResultSupport = store.getTable("scan_log_local_result_support")
        scanLogTicketUUIDSupport = store.getTable("scan_log_ticket_uuid_support")
        scanLogEventIdSupport = store.getTable("scan_log_ticket_event_id_support")
        //
    }

    //-----------

    open fun putAttendeeData(event: String, identifier: String, payload: String) {
        attendeeTable.put(attendeeKey(event, identifier), payload)
    }

    open fun isAttendeeDataPresent(event: String, identifier: String): Boolean {
        return attendeeTable.get(attendeeKey(event, identifier)) != null
    }

    open fun getAttendeeData(event: String, identifier: String): String? {
        return attendeeTable.getAsString(attendeeKey(event, identifier))

    }

    //-----------

    open fun putLastUpdated(event: String, lastUpdated: Long) {
        lastUpdatedTable.put(event, lastUpdated.toString())
    }

    open fun getLatestUpdate(event: String): Long {
        val res = lastUpdatedTable.getAsString(event)
        return if (res != null) {
            res.toLong()
        } else {
            -1
        }
    }
    //-----------

    open fun loadAllForEvent(eventId: Int): List<ScanLog> {
        val res = ArrayList<ScanLog>()
        val idToSearch = eventId.toString()
        scanLogEventIdSupport.keys().forEach { key ->
            if (idToSearch == scanLogEventIdSupport.getAsString(key)) {
                findOptionalById(key).ifPresent({res.add(it)})
            }
        }
        return res
    }

    fun insertScanLog(timestamp: ZonedDateTime?, eventId: Int, uuid: String, userId: Int, localResult: CheckInStatus, remoteResult: CheckInStatus, badgePrinted: Boolean, jsonPayload: String?) {
        val key = System.nanoTime().toString() + UUID.randomUUID().toString()
        val scanLogWithKey = ScanLog(key, timestamp!!, eventId, uuid, userId,
            localResult, remoteResult, badgePrinted, jsonPayload)
        putScanLong(scanLogWithKey)
    }

    private fun putScanLong(scanLog: ScanLog) {
        scanLogTable.put(scanLog.id, gson.toJson(scanLog))
        scanLogRemoteResultSupport.put(scanLog.id, scanLog.remoteResult.toString())
        scanLogLocalResultSupport.put(scanLog.id, scanLog.localResult.toString())
        scanLogTicketUUIDSupport.put(scanLog.id, scanLog.ticketUuid)
        scanLogEventIdSupport.put(scanLog.id, scanLog.eventId.toString())
    }

    open fun findById(key: String): ScanLog? {
        return findOptionalById(key).orElse(null)
    }

    open fun findOptionalById(key: String): Optional<ScanLog> {
        val res = scanLogTable.getAsString(key)
        return Optional.ofNullable(res).map { gson.fromJson(it, ScanLog::class.java) }
    }

    open fun findOptionalByIdAndEventId(key: String, eventId: Int): Optional<ScanLog> {
        return findOptionalById(key).filter({ scanLog -> scanLog.eventId == eventId })
    }

    open fun findRemoteFailures(): List<ScanLog> {
        val remoteFailures = ArrayList<ScanLog>()
        val retry = CheckInStatus.RETRY.toString()
        scanLogRemoteResultSupport.keys().forEach { key ->
            if (retry == scanLogRemoteResultSupport.getAsString(key)) {
                findOptionalById(key).ifPresent({ scanLog -> remoteFailures.add(scanLog) })
            }
        }
        return remoteFailures
    }

    fun loadSuccessfulScanForTicket(eventId: Int, ticketUuid: String): Optional<ScanLog> {
        val success = CheckInStatus.SUCCESS.toString()

        val keys = ArrayList<String>()
        //
        scanLogTicketUUIDSupport.keys().forEach { key ->
            if (ticketUuid == scanLogTicketUUIDSupport.getAsString(key)) {
                keys.add(key)
            }
        }
        //

        return keys.stream()
            .filter({ key -> success == scanLogLocalResultSupport.getAsString(key) })
            .map { key -> findById(key) }
            .filter(Objects::nonNull)
            .map { scanLog -> scanLog!! }
            .filter({ scanLog -> scanLog.eventId == eventId })
            .findFirst()
    }

    open fun updateRemoteResult(remoteResult: CheckInStatus, key: String) {
        findOptionalById(key).ifPresent({ scanLog ->
            val updatedScanLog = ScanLog(scanLog.id, scanLog.timestamp, scanLog.eventId, scanLog.ticketUuid,
                scanLog.userId, scanLog.localResult, remoteResult, scanLog.badgePrinted, scanLog.ticketData)
            putScanLong(updatedScanLog)
        })
    }

    fun loadNew(timestamp: Date): List<ScanLog> {
        return ArrayList() //TODO IMPLEMENT
    }

    fun loadPageAndTotalCount(offset: Int, pageSize: Int, search: String?): Pair<List<ScanLog>, Int> {
        return Pair(ArrayList(), 0) //TODO IMPLEMENT
    }

    fun isLeader(): Boolean {
        return store.isLeader
    }
}

private fun attendeeKey(event: String, identifier: String): String {
    return event + "_" + identifier
}