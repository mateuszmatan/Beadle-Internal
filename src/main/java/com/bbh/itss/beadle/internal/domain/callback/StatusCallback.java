package com.bbh.itss.beadle.internal.domain.callback;

import com.bbh.itss.beadle.internal.domain.calllog.ExternalSystem;
import com.bbh.itss.beadle.internal.domain.shared.CallTarget;

import java.time.Instant;

/**
 * The body of a callback to Beadle-GUI; the correlation id travels in the {@code X-Correlation-Id} header.
 */
public record StatusCallback(ExternalSystem system, String key, String status, Instant at) implements CallTarget {

    @Override
    public String callTarget() {
        return key + " " + status;
    }
}
