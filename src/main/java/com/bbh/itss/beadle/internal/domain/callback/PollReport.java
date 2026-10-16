package com.bbh.itss.beadle.internal.domain.callback;

import java.util.List;
import java.util.Map;

/**
 * What a poll did: the callbacks it {@code sent}, the tickets it could not follow up this time and tries again
 * ({@code failures}), and the tickets it {@code dropped} for good, each with its reason.
 */
public record PollReport(List<StatusCallback> sent, Map<String, String> failures, Map<String, String> dropped) {

    public static final PollReport NOTHING = new PollReport(List.of(), Map.of(), Map.of());
}
