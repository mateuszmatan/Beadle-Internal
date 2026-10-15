package com.bbh.itss.beadle.internal.domain.callback;

import java.util.List;
import java.util.Map;

public record PollReport(List<StatusCallback> sent, Map<String, String> failures) {

    public static final PollReport NOTHING = new PollReport(List.of(), Map.of());
}
