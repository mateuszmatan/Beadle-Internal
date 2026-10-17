package com.bbh.itss.beadle.internal.domain.cybertrack;

import java.time.Instant;

public record CyberTrackTicket(String key, String status, String url, Instant updated) {

    public static final String NAME = "CyberTrack ticket";
}
