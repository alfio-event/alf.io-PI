package io.alf.manager;

import com.google.gson.reflect.TypeToken;
import io.alf.App;
import io.alf.model.*;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

public class CheckinManager {

    private final ExternalResourceManager externalResourceManager;

    private QRCodeConfiguration configuration;
    private CheckinKVStore checkinKVStore;

    public CheckinManager(ExternalResourceManager externalResourceManager) {
        this.externalResourceManager = externalResourceManager;
    }

    public void load(QRCodeConfiguration qrCodeConfiguration) {

        if (checkinKVStore != null) {
            checkinKVStore.close();
        }

        // configure wifi here

        //

        configuration = qrCodeConfiguration;
        System.err.println("starting checkinkvstore");
        checkinKVStore = new CheckinKVStore(qrCodeConfiguration);
        System.err.println("started checkinkvstore");
        synchronizeDataWithEvent();
    }

    void synchronizeDataWithEvent() {
        System.err.println("sync data with event if necessary");
        syncAttendees();
        System.err.println("end sync data with event");
    }

    private void syncAttendees() {
        var lastUpdated = checkinKVStore.getLatestUpdate();
        var idsAndHeaders = loadIds(lastUpdated);
        if(idsAndHeaders != null) {
            var serverTime = Optional.ofNullable(idsAndHeaders.getHeader("Alfio-TIME")).map(Long::parseLong).orElse(1l);
            var ids = idsAndHeaders.result;
            int i = 0;
            System.err.println("found " + ids.length);
            for(; i < ids.length; i+= 200) {
                System.err.println("from "+i+" to " + (i+200));
                fetchAttendeesAndSave(Arrays.copyOfRange(ids, i, i+200));
            }
            checkinKVStore.putLastUpdated(serverTime);
        }
    }

    private void fetchAttendeesAndSave(long[] ids) {
        var url = configuration.url+"/admin/api/check-in/"+configuration.event+"/offline";
        var res = externalResourceManager.post(url, configuration.apiKey, null, ids, new TypeToken<Map<String, String>>(){});
        if(res != null) {
            System.err.println("found " + res.result.size());
            res.result.forEach((identifier, payload) -> {
                checkinKVStore.putAttendeeData(identifier, payload);
            });
        }
    }

    private ResultWithHeaders<long[]> loadIds(long lastUpdated) {
        var changedSinceParam = lastUpdated == -1 ? "" : "?changedSince="+lastUpdated;
        var url = configuration.url + "/admin/api/check-in/" + configuration.event + "/offline-identifiers" + changedSinceParam;
        return externalResourceManager.get(url, configuration.apiKey, null, long[].class);
    }

    public void stop() {
        if (checkinKVStore != null) {
            checkinKVStore.close();
        }
    }

    private String getAttendeeData(String key) {

        var attendeeData = checkinKVStore.getAttendeeData(key);

        if(!attendeeData.isPresent()) {
            System.err.println("calling syncAttendees before check-in");
            syncAttendees(); //ensure our copy is up to date
            attendeeData = checkinKVStore.getAttendeeData(key);
        }
        return attendeeData.orElse(null);
    }

    void getLocalTicketData(String uuid, String hmac) {
        var key = DigestUtils.sha256Hex(hmac);
        String result = getAttendeeData(key);
        if (result != null) {
            System.err.println("found encrypted data");
            var decryptedData = decrypt(uuid+"/"+hmac, result);
            System.err.println("data is " + decryptedData);
            var ticketData = App.JSON.fromJson(decryptedData, TicketData.class);
        }
    }

    public void checkIn(String uuidhmac) {
        var idxSlash = uuidhmac.indexOf('/');
        var uuid = uuidhmac.substring(0, idxSlash);
        var hmac = uuidhmac.substring(idxSlash+1);

        var maybeScanLog = checkinKVStore.getSuccessfulScanLog(uuid);

        if (maybeScanLog.isPresent()) {
            //
            System.err.println("Already checked in");
            return;
        } else {
            getLocalTicketData(uuid, hmac);
            //check in
            checkinKVStore.addScan(new ScanLog(uuid, ScanLog.ScanType.CHECK_IN, ScanLog.ScanStatus.SUCCESS, ScanLog.ScanStatus.RETRY));
            checkinKVStore.addScan(new ScanLog(uuid, ScanLog.ScanType.BADGE, ScanLog.ScanStatus.SUCCESS, ScanLog.ScanStatus.RETRY));
        }

    }

    private String decrypt(String key, String payload) {
        try {
            var cipherAndSecret = getCypher(key);
            var cipher = cipherAndSecret.first;
            var idx = payload.indexOf('|');
            var iv = Base64.getUrlDecoder().decode(payload.substring(0, idx));
            var body = Base64.getUrlDecoder().decode(payload.substring(idx+1));
            cipher.init(Cipher.DECRYPT_MODE, cipherAndSecret.second, new IvParameterSpec(iv));
            var decrypted = cipher.doFinal(body);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private Pair<Cipher, SecretKeySpec> getCypher(String key)  {
        try {
            var factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            var iterations = 1000;
            var keyLength = 256;
            var spec = new PBEKeySpec(key.toCharArray(), key.getBytes(StandardCharsets.UTF_8), iterations, keyLength);
            var secretKey = factory.generateSecret(spec);
            var secret = new SecretKeySpec(secretKey.getEncoded(), "AES");
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            return new Pair<>(cipher, secret);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
