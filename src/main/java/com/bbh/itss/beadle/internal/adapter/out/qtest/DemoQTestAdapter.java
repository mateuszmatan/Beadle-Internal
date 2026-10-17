package com.bbh.itss.beadle.internal.adapter.out.qtest;

import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort;
import com.bbh.itss.beadle.internal.domain.qtest.QTestCycle;
import com.bbh.itss.beadle.internal.domain.qtest.QTestProject;
import com.bbh.itss.beadle.internal.domain.qtest.QTestRun;
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.bbh.itss.beadle.internal.adapter.out.qtest.QTestProperties.NOT_CONFIGURED;
import static com.bbh.itss.beadle.internal.domain.qtest.QTestSummary.BLOCKED;
import static com.bbh.itss.beadle.internal.domain.qtest.QTestSummary.FAILED;
import static com.bbh.itss.beadle.internal.domain.qtest.QTestSummary.PASSED;
import static com.bbh.itss.beadle.internal.domain.qtest.QTestSummary.UNEXECUTED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.internal.domain.shared.Timestamps.now;
import static java.time.Duration.ofHours;
import static java.time.temporal.ChronoUnit.DAYS;

@Component
@ConditionalOnExpression(NOT_CONFIGURED)
@RequiredArgsConstructor
class DemoQTestAdapter implements QTestPort {

    static final List<QTestProject> PROJECTS = List.of(new QTestProject(1, "CertScanner"),
            new QTestProject(2, "PayHub"));
    static final Map<Long, List<DemoCycle>> CYCLES = Map.of(
            1L, List.of(new DemoCycle(new QTestCycle(10, "Release 1.3", "CL-10"), 28, 45, true),
                    new DemoCycle(new QTestCycle(11, "Release 1.4", "CL-11"), 34, 2, false),
                    new DemoCycle(new QTestCycle(12, "Nightly regression", "CL-12"), 60, 1, true)),
            2L, List.of(new DemoCycle(new QTestCycle(20, "Release 3.2", "CL-20"), 42, 30, true),
                    new DemoCycle(new QTestCycle(21, "Smoke", "CL-21"), 12, 0, false)));
    static final List<String> TESTS = List.of("Sign in with SSO", "Sign out of every session", "Open the dashboard",
            "Search by reference", "Page through the results", "Export the grid to Excel", "Create a record",
            "Edit a record", "Delete a record", "Audit trail of a record", "Upload a document",
            "Download a document", "Daily report by e-mail", "Reconcile positions", "Mark a break as explained",
            "Keyboard navigation of the dialogs", "Screen reader names", "Contrast of the status colours",
            "Retry a timed-out call", "Circuit breaker opens", "Alert on a dependency down",
            "Archive closed records", "Legal hold", "Load the ISIN master file", "Check ISO 4217 currencies",
            "Teams alert on failure", "Notification centre", "Unsubscribe link", "Onboarding wizard",
            "Welcome pack");

    private final Clock clock;

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public List<QTestProject> projects() {
        return PROJECTS;
    }

    @Override
    public List<QTestCycle> cycles(long projectId) {
        return cyclesOf(projectId).stream().map(DemoCycle::cycle).toList();
    }

    @Override
    public List<QTestRun> runs(long projectId, long cycleId) {
        DemoCycle found = cyclesOf(projectId).stream().filter(cycle -> cycle.cycle().id() == cycleId).findFirst()
                .orElseThrow(() -> notFound(QTestCycle.NAME, cycleId));
        return found.runs(now(clock).truncatedTo(DAYS));
    }

    @Override
    public QTestSummary summary(long projectId, long cycleId) {
        return QTestSummary.of(runs(projectId, cycleId));
    }

    private static List<DemoCycle> cyclesOf(long projectId) {
        List<DemoCycle> cycles = CYCLES.get(projectId);
        if (cycles == null) {
            throw notFound(QTestProject.NAME, projectId);
        }
        return cycles;
    }

    record DemoCycle(QTestCycle cycle, int runs, int daysAgo, boolean finished) {

        List<QTestRun> runs(Instant today) {
            Random random = new Random(cycle.id());
            Instant executed = today.minus(daysAgo, DAYS).plus(ofHours(9));
            List<QTestRun> result = new ArrayList<>();
            for (int index = 0; index < runs; index++) {
                long id = cycle.id() * 100 + index;
                String name = TESTS.get(index % TESTS.size()) + (index < TESTS.size() ? "" : " " + (index / TESTS.size() + 1));
                String status = statusOf(index, random);
                result.add(new QTestRun(id, name, "TR-" + id, status,
                        UNEXECUTED.equals(status) ? null : executed.plus(ofHours(index % 8))));
            }
            return result;
        }

        private String statusOf(int index, Random random) {
            if (!finished && index >= runs * 3 / 4) {
                return UNEXECUTED;
            }
            int roll = random.nextInt(20);
            return roll == 0 ? FAILED : roll == 1 && !finished ? BLOCKED : PASSED;
        }
    }
}
