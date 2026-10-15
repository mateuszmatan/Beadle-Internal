package com.bbh.itss.beadle.internal.domain.status;

public record SystemStatus(boolean connected, String mode) {

    public static final String LIVE = "live";
    public static final String DEMO = "demo";

    public static SystemStatus of(boolean connected) {
        return new SystemStatus(connected, connected ? LIVE : DEMO);
    }
}
