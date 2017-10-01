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
    private val scanLogTableSupport: SyncKV.SyncKVTable

    init {
        attendeeTable = store.getTable("attendee")
        lastUpdatedTable = store.getTable("last_updated")

        //
        scanLogTable = store.getTable("scan_log")
        scanLogTableSupport = store.getTable("scan_log_support")
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

        findAllIdsWith("event_id", eventId.toString()).forEach({
            findOptionalById(it).ifPresent({scanLog -> res.add(scanLog)})
        })
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
        scanLogTableSupport.put(scanLog.id,
            "|remote_result:" + scanLog.remoteResult.toString() +
            "|local_result:" + scanLog.localResult.toString() +
            "|ticket_uuid:" + scanLog.ticketUuid +
            "|event_id:" + scanLog.eventId.toString() + "|"
        )
    }

    private fun findAllIdsWith(nameValuePairs: Array<Pair<String, String>>): List<String> {
        val toCheck = nameValuePairs.map { "|"+it.first+":"+it.second+"|" }
        val matching = ArrayList<String>()
        scanLogTableSupport.keys().forEach {
            val idx = scanLogTableSupport.getAsString(it)
            var match = true
            for (check in toCheck) {
                if (idx.indexOf(check) < 0) {
                    match = false
                    break
                }
            }

            if (match) {
                matching.add(it)
            }
        }
        return matching
    }

    private fun findAllIdsWith(name: String, value: String): List<String> {
        return findAllIdsWith(arrayOf(Pair(name, value)))
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
        findAllIdsWith("remote_result", CheckInStatus.RETRY.toString()).forEach({key ->
            findOptionalById(key).ifPresent({ scanLog -> remoteFailures.add(scanLog) })
        })
        return remoteFailures
    }

    fun loadSuccessfulScanForTicket(eventId: Int, ticketUuid: String): Optional<ScanLog> {
        val found = findAllIdsWith(arrayOf(Pair("ticket_uuid", ticketUuid),
            Pair("local_result", CheckInStatus.SUCCESS.toString()),
            Pair("event_id", eventId.toString())))

        if(found.isEmpty()) {
            return Optional.empty()
        } else {
            return findOptionalById(found.first())
        }
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