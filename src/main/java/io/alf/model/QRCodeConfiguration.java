package io.alf.model;

public class QRCodeConfiguration {

    public final String url;
    public final String apiKey;
    public final String event;
    public final String ssid;
    public final String password;
    public boolean checkInForcePaymentOnSite;

    public QRCodeConfiguration(String url, String apiKey, String event, String ssid, String password) {
        this.url = url;
        this.apiKey = apiKey;
        this.event = event;
        this.ssid = ssid;
        this.password = password;
    }
}
