package alfio.pi.manager

import alfio.pi.RemoteApiAuthenticationDescriptor
import alfio.pi.model.*
import ch.digitalfondue.synckv.SyncKV
import ch.digitalfondue.synckv.SyncKVStructuredTable
import ch.digitalfondue.synckv.SyncKVTable
import com.google.gson.Gson
import org.jgroups.util.Tuple
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.token.Sha512DigestUtils
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.ArrayList


private val logger: Logger = LoggerFactory.getLogger("alfio.pi.manager.KVStore")

@Component
open class KVStore(private val gson: Gson, authenticationDescriptor: RemoteApiAuthenticationDescriptor) {

    private val store: SyncKV = SyncKV(synckvFileName(authenticationDescriptor.url), authenticationDescriptor.apiKey)

    private val attendeeTable: SyncKVTable
    //
    private val lastUpdatedTable: SyncKVTable
    //
    private val scanLogTable: SyncKVStructuredTable<ScanLogToPersist>
    private val scanLogForEventTicket: SyncKVTable
    //
    private val labelConfigurationTable: SyncKVTable
    private val remainingLabels: SyncKVTable
    private val badgeScanTable: SyncKVStructuredTable<BadgeScan>
    private val badgeScanLogTable: SyncKVStructuredTable<ScanLog>


    init {
        attendeeTable = store.getTable("attendee")
        lastUpdatedTable = store.getTable("last_updated")
        //
        scanLogForEventTicket = store.getTable("scan_log_event_ticket")
        scanLogTable = store.getTable("scan_log").toStructured(ScanLogToPersist::class.java, { data ->
            ScanLogToPersist(
                data.readUTF(),
                data.readLong(),
                data.readUTF(),
                data.readUTF(),
                data.readUTF(),
                data.readInt(),
                CheckInStatus.valueOf(data.readUTF()),
                CheckInStatus.valueOf(data.readUTF()),
                data.readBoolean(),
                nullIfEmpty(data.readUTF())
            )
        }, {scanLogToPersist, out ->
            out.writeUTF(scanLogToPersist.id)
            out.writeLong(scanLogToPersist.timestamp)
            out.writeUTF(scanLogToPersist.zoneId)
            out.writeUTF(scanLogToPersist.eventKey)
            out.writeUTF(scanLogToPersist.ticketUuid)
            out.writeInt(scanLogToPersist.userId)
            out.writeUTF(scanLogToPersist.localResult.toString())
            out.writeUTF(scanLogToPersist.remoteResult.toString())
            out.writeBoolean(scanLogToPersist.badgePrinted)
            out.writeUTF(scanLogToPersist.ticketData.orEmpty())
        })
        //scanLogTableSupport = store.getTable("scan_log_support")
        //
        labelConfigurationTable = store.getTable("label_configuration")
        remainingLabels = store.getTable("printer_remaining_labels")

        badgeScanTable = store.getTable("badge_scan")
            .toStructured(BadgeScan::class.java, { data ->
                BadgeScan(
                    data.readUTF(),
                    CheckInStatus.valueOf(data.readUTF()),
                    ZonedDateTime.parse(data.readUTF()),
                    ZonedDateTime.parse(data.readUTF()),
                    ZonedDateTime.parse(data.readUTF()),
                    data.readUTF(),
                    CheckInStrategy.valueOf(data.readUTF())
                )
            }, { badgeScan, out ->
                out.writeUTF(badgeScan.ticketIdentifier)
                out.writeUTF(badgeScan.localStatus.name)
                out.writeUTF(badgeScan.timestamp.toString())
                out.writeUTF(badgeScan.ticketValidityFrom.toString())
                out.writeUTF(badgeScan.ticketValidityTo.toString())
                out.writeUTF(badgeScan.categoryName)
                out.writeUTF(badgeScan.checkInStrategy.name)
            })

        badgeScanLogTable = store.getTable("badge_scan_log")
            .toStructured(ScanLog::class.java,
                {data ->
                    ScanLog(
                        data.readUTF(),
                        ZonedDateTime.parse(data.readUTF()),
                        data.readUTF(),
                        data.readUTF(),
                        data.readUTF().toInt(),
                        CheckInStatus.valueOf(data.readUTF()),
                        CheckInStatus.valueOf(data.readUTF()),
                        data.readUTF().toBoolean(),
                        nullIfEmpty(data.readUTF())
                    )
                },
                {scanLog, out ->
                    out.writeUTF(scanLog.id)
                    out.writeUTF(scanLog.timestamp.toString())
                    out.writeUTF(scanLog.eventKey)
                    out.writeUTF(scanLog.ticketUuid)
                    out.writeUTF(scanLog.userId.toString())
                    out.writeUTF(scanLog.localResult.name)
                    out.writeUTF(scanLog.remoteResult.name)
                    out.writeUTF(scanLog.badgePrinted.toString())
                    out.writeUTF(scanLog.ticketData.orEmpty())
                })
    }

    private fun nullIfEmpty(s: String): String? = if (s.isEmpty()) null else s

    data class ScanLogToPersist(val id: String,
                                val timestamp: Long,
                                val zoneId: String,
                                val eventKey: String,
                                val ticketUuid: String,
                                val userId: Int,
                                val localResult: CheckInStatus,
                                val remoteResult: CheckInStatus,
                                val badgePrinted: Boolean,
                                val ticketData: String?) {


        fun toSearch(): String {
            return if(ticketData == null) {
                ""
            } else {
                val ticket = GsonContainer.GSON?.fromJson(ticketData, Ticket::class.java)!!
                (ticket.firstName+" " +ticket.lastName + " " + ticket.email).toLowerCase(Locale.ENGLISH)
            }
        }

        fun toScanLog(): ScanLog {
            return ScanLog(id,
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of(zoneId)),
                eventKey, ticketUuid, userId, localResult, remoteResult, badgePrinted, ticketData
            )
        }
    }

    //-----------

    open fun putRemainingLabels(printer: String, labels: Int) {
        remainingLabels.put(printer, labels.toString(10))
    }

    open fun getRemainingLabels(printer: String) : Int {
        return remainingLabels.getAsString(printer)?.toInt() ?: 0
    }

    open fun putAttendeeData(event: String, identifier: String, payload: String) {
        attendeeTable.put(attendeeKey(event, identifier), payload)
    }

    open fun isAttendeeDataPresent(event: String, identifier: String): Boolean {
        return attendeeTable.get(attendeeKey(event, identifier)) != null
    }

    open fun getAttendeeData(event: String, identifier: String): String? {
        return attendeeTable.getAsString(attendeeKey(event, identifier))
    }

    open fun getAttendeeDataCount() : Int {
        return attendeeTable.count()
    }

    //-----------

    open fun putLastUpdated(event: String, lastUpdated: Long) {
        lastUpdatedTable.put(event, lastUpdated.toString())
    }

    open fun getLatestUpdate(event: String): Long {
        val res = lastUpdatedTable.getAsString(event)
        return res?.toLong() ?: -1
    }
    //-----------

    open fun loadAllForEvent(eventKey: String): List<ScanLog> {
        return scanLogTable.stream()
            .filter {it.value.eventKey == eventKey}
            .map { it.value.toScanLog() }
            .collect(Collectors.toList())
    }

    open fun insertScanLog(eventKey: String, uuid: String, userId: Int, localResult: CheckInStatus, remoteResult: CheckInStatus, badgePrinted: Boolean, jsonPayload: String?) {
        val timestamp = ZonedDateTime.now()
        val key = scanLogId()
        val scanLogWithKey = ScanLogToPersist(key, timestamp.toInstant().toEpochMilli(), timestamp.zone.id, eventKey, uuid, userId,
            localResult, remoteResult, badgePrinted, jsonPayload)
        putScanLog(scanLogWithKey)
    }

    open fun insertBadgeScan(eventKey: String, badgeScan: BadgeScan) =
        badgeScanTable.put(attendeeKey(eventKey, badgeScan.ticketIdentifier), badgeScan)

    open fun retrieveBadgeScan(eventKey: String, ticketUuid: String): BadgeScan? =
        badgeScanTable.get(attendeeKey(eventKey, ticketUuid))

    open fun insertBadgeScanLog(eventKey: String, uuid: String, userId: Int, localResult: CheckInStatus, remoteResult: CheckInStatus, timestamp: ZonedDateTime) {
        val scanLogId = scanLogId()
        val scanLog = ScanLog(scanLogId, timestamp, eventKey, uuid, userId, localResult, remoteResult, false, null)
        badgeScanLogTable.put(scanLogId, scanLog)
    }


    open fun selectBadgeScanToSynchronize(): MutableList<ScanLog> = badgeScanLogTable.stream()
        .filter { it.value.remoteResult == CheckInStatus.RETRY }
        .map { it.value }
        .limit(100)
        .collect(Collectors.toList())

    private fun putScanLog(scanLog: ScanLogToPersist) {
        scanLogTable.put(scanLog.id, scanLog)
        scanLogForEventTicket.put(scanLog.eventKey + "_" + scanLog.ticketUuid, scanLog.id)
    }

    private fun searchScanLog(term: String?) : List<String> {
        val matching = ArrayList<Triple<String, Long, String>>()
        val termLowerCase = term?.toLowerCase(Locale.ENGLISH)
        scanLogTable.keys().forEach {
            val idx = scanLogTable.get(it)
            if(termLowerCase == null || idx.toSearch().indexOf(termLowerCase) >= 0) {
                matching.add(Triple(it, idx.timestamp, idx.ticketUuid))
            }
        }

        return matching.asSequence().sortedWith(compareByDescending<Triple<String, Long, String>> { it.second }.thenBy {it.third}).map { it.first }.toList()
    }

    open fun findById(key: String): ScanLog? {
        return findOptionalById(key).orElse(null)
    }

    open fun findOptionalById(key: String): Optional<ScanLog> {
        val res = scanLogTable.get(key)
        return Optional.ofNullable(res).map {it.toScanLog()}
    }

    open fun findOptionalByIdAndEventKey(key: String, eventKey: String): Optional<ScanLog> {
        return findOptionalById(key).filter { scanLog -> scanLog.eventKey == eventKey }
    }

    open fun findCheckInToUpload(): List<ScanLog> {
        return scanLogTable.stream()
            .filter{ it.value.remoteResult == CheckInStatus.RETRY}
            .map { it.value.toScanLog() }
            .collect(Collectors.toList())
    }

    open fun loadSuccessfulScanForTicket(eventKey: String, ticketUuid: String): Optional<ScanLog> {
        return Optional.ofNullable(scanLogForEventTicket.getAsString(eventKey + "_" + ticketUuid))
            .flatMap { Optional.ofNullable(scanLogTable.get(it)) }
            .filter { CheckInStatus.SUCCESS == it.localResult }
            .map { it.toScanLog() }
    }

    open fun updateRemoteResult(remoteResult: CheckInStatus, key: String) {
        findOptionalById(key).ifPresent { scanLog ->
            val updatedScanLog = ScanLogToPersist(scanLog.id, scanLog.timestamp.toInstant().toEpochMilli(), scanLog.timestamp.zone.id, scanLog.eventKey, scanLog.ticketUuid,
                scanLog.userId, scanLog.localResult, remoteResult, scanLog.badgePrinted, scanLog.ticketData)
            putScanLog(updatedScanLog)
        }
    }

    open fun updateBadgeScanRemoteResult(remoteResult: CheckInStatus, badgeScanLog: ScanLog) {
        badgeScanLogTable.put(badgeScanLog.id, badgeScanLog.copy(remoteResult = remoteResult))
    }

    fun loadNew(timestamp: Date): List<ScanLog> {

        val timestampConverted = timestamp.toInstant().toEpochMilli()
        val matching = ArrayList<Tuple<String, Long>>()
        scanLogTable.keys().forEach {
            val scanLog = scanLogTable.get(it)
            val scanTs = scanLog.timestamp
            if(scanTs > timestampConverted) {
                matching.add(Tuple(it, scanTs))
            }
        }

        return matching
            .asSequence()
            .sortedWith(compareByDescending<Tuple<String, Long>> { it.val2 }.thenBy { it.val1 })
            .map { findById(it.val1)!! }
            .toList()
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

    //----------

    data class PayloadAndEnabled(val payload: String?, val enabled: Boolean)

    fun saveLabelConfiguration(eventKey: String, payload: String?, enabled: Boolean) {
        val toSave = PayloadAndEnabled(payload, enabled)
        labelConfigurationTable.put(eventKey, gson.toJson(toSave))
    }

    open fun loadLabelConfiguration(eventKey: String) : Optional<LabelConfiguration> {
        val res = labelConfigurationTable.getAsString(eventKey)
        logger.trace("loaded labelConfiguration: {}", res)
        return if (res == null) {
            Optional.empty()
        } else {
            val toLoad = gson.fromJson(res, PayloadAndEnabled::class.java)
            Optional.of(LabelConfiguration(eventKey, toLoad.payload, toLoad.enabled))
        }
    }

    fun isLeader(): Boolean {
        return store.isLeader
    }

    fun getClusterMemberName(): String {
        return store.clusterMemberName
    }

    fun getClusterMembersName(): List<String> {
        return store.clusterMembersName
    }
}

private fun attendeeKey(event: String, identifier: String) = "${event}_$identifier"

private fun scanLogId() = System.currentTimeMillis().toString() + UUID.randomUUID().toString()

private fun synckvFileName(remoteUrl: String): String =
    "alfio-pi-synckv-${Sha512DigestUtils.shaHex(remoteUrl).substring(IntRange(0, 10))}"