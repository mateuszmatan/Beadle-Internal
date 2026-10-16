package com.bbh.itss.beadle.internal.domain.qtest;

import java.util.Collection;

import static org.apache.commons.lang3.Strings.CI;

public record QTestSummary(int total, int passed, int failed, int blocked, int unexecuted) {

    public static final String PASSED = "Passed";
    public static final String FAILED = "Failed";
    public static final String BLOCKED = "Blocked";
    public static final String UNEXECUTED = "Unexecuted";

    public static QTestSummary of(Collection<QTestRun> runs) {
        int passed = count(runs, PASSED);
        int failed = count(runs, FAILED);
        int blocked = count(runs, BLOCKED);
        return new QTestSummary(runs.size(), passed, failed, blocked, runs.size() - passed - failed - blocked);
    }

    private static int count(Collection<QTestRun> runs, String status) {
        return (int) runs.stream().filter(run -> CI.equals(run.status(), status)).count();
    }
}
