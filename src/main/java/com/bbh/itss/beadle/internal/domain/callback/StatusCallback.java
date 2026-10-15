package com.bbh.itss.beadle.internal.domain.callback;

import com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem;
import com.bbh.itss.beadle.internal.domain.shared.CallTarget;

import java.time.Instant;

public record StatusCallback(ExternalSystem system, String key, String status, Instant at, String correlationId)
        implements CallTarget {

    @Override
    public String callTarget() {
        return key + " " + status;
    }
}
