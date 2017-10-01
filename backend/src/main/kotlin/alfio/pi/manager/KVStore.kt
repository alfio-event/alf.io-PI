package alfio.pi.manager

import alfio.pi.model.CheckInStatus
import alfio.pi.model.GsonContainer
import alfio.pi.model.ScanLog
import alfio.pi.model.Ticket
import ch.digitalfondue.synckv.SyncKV
import com.google.gson.Gson
import org.jgroups.util.Tuple
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
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

    data class ScanLogToPersist(val id: String,
                                val timestamp: Long,
                                val zoneId: String,
                                val eventId: Int,
                                val ticketUuid: String,
                                val userId: Int,
                                val localResult: CheckInStatus,
                                val remoteResult: CheckInStatus,
                                val badgePrinted: Boolean,
                                val ticketData: String?)

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

    fun insertScanLog(eventId: Int, uuid: String, userId: Int, localResult: CheckInStatus, remoteResult: CheckInStatus, badgePrinted: Boolean, jsonPayload: String?) {
        val timestamp = ZonedDateTime.now()
        val key = System.nanoTime().toString() + UUID.randomUUID().toString()
        val scanLogWithKey = ScanLogToPersist(key, timestamp.toInstant().toEpochMilli(), timestamp.zone.id, eventId, uuid, userId,
            localResult, remoteResult, badgePrinted, jsonPayload)
        putScanLong(scanLogWithKey)
    }

    private fun putScanLong(scanLog: ScanLogToPersist) {

        scanLogTable.put(scanLog.id, gson.toJson(scanLog))

        val toSearch = if(scanLog.ticketData == null) {
            ""
        } else {
            val ticket = GsonContainer.GSON?.fromJson(scanLog.ticketData, Ticket::class.java)!!
            (ticket.firstName+" " +ticket.lastName + " " + ticket.email).toLowerCase(Locale.ENGLISH)
        }

        scanLogTableSupport.put(scanLog.id,
                "|||remote_result:" + scanLog.remoteResult.toString() +
                "|||local_result:" + scanLog.localResult.toString() +
                "|||ticket_uuid:" + scanLog.ticketUuid +
                "|||event_id:" + scanLog.eventId.toString() +
                "|||to_search:" + toSearch +
                "|||scan_ts:" + scanLog.timestamp.toString() + "|||"
        )
    }

    private fun extractField(name: String, idx: String) : String {
        val fieldName = "|||"+name+":"
        val boundary1 = idx.indexOf(fieldName)
        val boundary2 = idx.indexOf("|||", boundary1 + 1)
        return idx.substring(boundary1+fieldName.length, boundary2)
    }

    private fun searchScanLog(term: String?) : List<String> {
        val matching = ArrayList<Triple<String, Long, String>>()
        val termLowerCase = term?.toLowerCase(Locale.ENGLISH)
        scanLogTableSupport.keys().forEach {
            val idx = scanLogTableSupport.getAsString(it)
            if(termLowerCase == null || extractField("to_search", idx).indexOf(termLowerCase) >= 0) {
                matching.add(Triple(it, extractField("scan_ts", idx).toLong(), extractField("ticket_uuid", idx)))
            }
        }

        return matching.sortedWith(compareByDescending<Triple<String, Long, String>> { it.second }.thenBy {it.third}).map { it.first }
    }

    private fun findAllIdsWith(nameValuePairs: Array<Pair<String, String>>): List<String> {
        val toCheck = nameValuePairs.map { "|||"+it.first+":"+it.second+"|||" }
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
        return Optional.ofNullable(res).map { gson.fromJson(it, ScanLogToPersist::class.java) }
            .map {ScanLog(it.id,
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp), ZoneId.of(it.zoneId)),
                it.eventId, it.ticketUuid, it.userId, it.localResult, it.remoteResult, it.badgePrinted, it.ticketData
                )
            }
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
            val updatedScanLog = ScanLogToPersist(scanLog.id, scanLog.timestamp.toEpochSecond(), scanLog.timestamp.zone.id, scanLog.eventId, scanLog.ticketUuid,
                scanLog.userId, scanLog.localResult, remoteResult, scanLog.badgePrinted, scanLog.ticketData)
            putScanLong(updatedScanLog)
        })
    }

    fun loadNew(timestamp: Date): List<ScanLog> {

        val timestampConverted = timestamp.toInstant().epochSecond
        val matching = ArrayList<Tuple<String, Long>>()
        scanLogTableSupport.keys().forEach {
            val idx = scanLogTableSupport.getAsString(it)
            val scanTs = extractField("scan_ts", idx).toLong()
            if(scanTs > timestampConverted) {
                matching.add(Tuple(it, scanTs))
            }
        }

        return matching
            .sortedWith(compareByDescending<Tuple<String, Long>> { it.val2 }.thenBy { it.val1 })
            .map { findById(it.val1)!! }
    }

    fun loadPageAndTotalCount(offset: Int, pageSize: Int, search: String?): Pair<List<ScanLog>, Int> {

        val found = searchScanLog(search)
        val count = found.size
        val selectedPage = if (found.isEmpty()) {
            ArrayList()
        } else {
            found.subList(offset, Math.min(found.size, offset + pageSize)).map { findById(it)!! }
        }

        return Pair(selectedPage, count)
    }

    fun isLeader(): Boolean {
        return store.isLeader
    }
}

private fun attendeeKey(event: String, identifier: String): String {
    return event + "_" + identifier
}