package com.bbh.itss.beadle.internal.domain.qtest;

import java.time.Instant;

public record QTestRun(long id, String name, String pid, String status, Instant executedAt) {
}
