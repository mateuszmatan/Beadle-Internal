package com.bbh.itss.beadle.internal.domain.calllog;

import java.time.Instant;

public record CallLogEntry(Long id, Instant at, String userName, String correlationId, ExternalSystem system,
                           String operation, String target, Outcome outcome, String message, long durationMs) {

    public static final int USER_MAX = 200;
    public static final int CORRELATION_MAX = 100;
    public static final int TARGET_MAX = 500;
    public static final int MESSAGE_MAX = 2000;
}
