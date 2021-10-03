package io.alf.manager;

import ch.digitalfondue.synckv.SyncKV;
import ch.digitalfondue.synckv.SyncKVStructuredTable;
import io.alf.model.QRCodeConfiguration;
import io.alf.model.ScanLog;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class CheckinKVStore {

    private final SyncKV syncKV;
    private final QRCodeConfiguration configuration;

    //
    private final SyncKVStructuredTable<String> attendeeTable;
    private final SyncKVStructuredTable<Long> lastUpdatedTable;
    private final SyncKVStructuredTable<ScanLog> scanLogTable;
    private final SyncKVStructuredTable<String> checkinMappingTable;
    private final SyncKVStructuredTable<String> badgeScanMappingTable;
    //


    CheckinKVStore(QRCodeConfiguration configuration) {
        this.configuration = configuration;
        var url = configuration.url;
        var apiKey = configuration.apiKey;
        String key = DigestUtils.sha256Hex(url + apiKey + configuration.event);
        try {
            Files.createDirectory(Path.of("data"));
        } catch (IOException e) {
        }
        this.syncKV = new SyncKV("data/" + key, key, key);

        attendeeTable = syncKV.getTable("attendee").toStructured(String.class, (dais) -> dais.readUTF(), (a, daos) -> daos.writeUTF(a));
        lastUpdatedTable = syncKV.getTable("last_updated").toStructured(Long.class, (dais) -> dais.readLong(), (a, daos) -> daos.writeLong(a.longValue()));
        scanLogTable = syncKV.getTable("scan_log").toStructured(ScanLog.class, (dais) -> new ScanLog(dais.readUTF(), null, null, null), (a, daos) -> {daos.writeUTF(a.id);});
        checkinMappingTable = syncKV.getTable("checkin_mapping").toStructured(String.class, (dais) -> dais.readUTF(), (a, daos) -> daos.writeUTF(a));
        badgeScanMappingTable = syncKV.getTable("badgescan_mapping").toStructured(String.class, (dais) -> dais.readUTF(), (a, daos) -> daos.writeUTF(a));
    }

    void putAttendeeData(String identifier, String payload) {
        attendeeTable.put(identifier, payload);
    }

    Optional<String> getAttendeeData(String identifier) {
        return Optional.ofNullable(attendeeTable.get(identifier));
    }

    List<ScanLog> findBadgeScanToSynchronize() {
        return scanLogTable.stream().map(Map.Entry::getValue)
                .filter(scanLog -> scanLog.type == ScanLog.ScanType.BADGE && scanLog.remoteStatus == ScanLog.ScanStatus.RETRY)
                .limit(100).collect(Collectors.toList());
    }

    List<ScanLog> findCheckInToUpload() {
        return scanLogTable.stream().map(Map.Entry::getValue)
                .filter(scanLog -> scanLog.type == ScanLog.ScanType.CHECK_IN && scanLog.remoteStatus == ScanLog.ScanStatus.RETRY)
                .limit(100).collect(Collectors.toList());
    }

    Optional<ScanLog> getBadgeScan(String uuid) {
        return Optional.ofNullable(badgeScanMappingTable.get(uuid)).map(scanLogTable::get);
    }

    Optional<ScanLog> getCheckinScan(String uuid) {
        return Optional.ofNullable(checkinMappingTable.get(uuid)).map(scanLogTable::get);
    }

    Optional<ScanLog> getSuccessfulScanLog(String uuid) {
        return getCheckinScan(uuid).filter(scanLog -> scanLog.localStatus == ScanLog.ScanStatus.SUCCESS);
    }

    public void addScan(ScanLog scanLog) {
        String id = uniqueId();
        scanLogTable.put(id, scanLog);
        if (scanLog.type == ScanLog.ScanType.BADGE) {
            badgeScanMappingTable.put(scanLog.id, id);
        } else {
            checkinMappingTable.put(scanLog.id, id);
        }
    }


    int getAttendeeDataCount() {
        return attendeeTable.count();
    }

    void putLastUpdated(long lastUpdated) {
        lastUpdatedTable.put(configuration.event, lastUpdated);
    }

    long getLatestUpdate() {
        var res = lastUpdatedTable.get(configuration.event);
        return res != null ? res : -1;
    }

    public void close() {
        syncKV.close();
    }

    private String uniqueId() {
        return System.currentTimeMillis() + UUID.randomUUID().toString();
    }
}
