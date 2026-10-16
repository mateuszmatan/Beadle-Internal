package com.bbh.itss.beadle.internal.domain.calllog;

import java.util.List;

public record CallLogPage(int page, int size, long total, List<CallLogEntry> entries) {
}
