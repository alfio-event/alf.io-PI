package io.alf.model;

public class ScanLog {

    public final String id;
    public final ScanType type;
    public final ScanStatus localStatus;
    public final ScanStatus remoteStatus;

    public ScanLog(String id, ScanType type, ScanStatus localStatus, ScanStatus remoteStatus) {
        this.id = id;
        this.type = type;
        this.localStatus = localStatus;
        this.remoteStatus = remoteStatus;
    }


    public enum ScanType {
        CHECK_IN, BADGE;
    }

    public enum ScanStatus {
        RETRY,
        EVENT_NOT_FOUND,
        TICKET_NOT_FOUND,
        EMPTY_TICKET_CODE,
        INVALID_TICKET_CODE,
        INVALID_TICKET_STATE,
        ALREADY_CHECK_IN,
        MUST_PAY,
        OK_READY_TO_BE_CHECKED_IN(true),
        SUCCESS(true),
        BADGE_SCAN_ALREADY_DONE(false, true),
        BADGE_SCAN_SUCCESS(true),
        INVALID_TICKET_CATEGORY_CHECK_IN_DATE;

        ScanStatus() {
        }
        ScanStatus(boolean successful) {
            this.successful = successful;
        }
        ScanStatus(boolean successful, boolean warning) {
            this.successful = successful;
            this.warning = warning;
        }

        boolean successful = false;
        boolean warning = false;
    }
}
