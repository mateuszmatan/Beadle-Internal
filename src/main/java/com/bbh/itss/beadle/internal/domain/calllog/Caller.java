package com.bbh.itss.beadle.internal.domain.calllog;

public record Caller(String user, String correlationId) {

    public static final Caller NONE = new Caller(null, null);
}
