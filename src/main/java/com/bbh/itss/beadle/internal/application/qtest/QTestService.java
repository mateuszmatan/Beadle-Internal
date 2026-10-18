package com.bbh.itss.beadle.internal.application.qtest;

import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.WithoutTransaction;
import com.bbh.itss.beadle.internal.application.qtest.port.in.QTestUseCase;
import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort;
import com.bbh.itss.beadle.internal.domain.qtest.QTestCycle;
import com.bbh.itss.beadle.internal.domain.qtest.QTestProject;
import com.bbh.itss.beadle.internal.domain.qtest.QTestRun;
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary;
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.bbh.itss.beadle.internal.domain.shared.ValidationProblems.REQUIRED;

@UseCase
@RequiredArgsConstructor
public class QTestService implements QTestUseCase {

    private final QTestPort qtest;

    @Override
    @WithoutTransaction
    public List<QTestProject> projects() {
        return qtest.projects();
    }

    @Override
    @WithoutTransaction
    public List<QTestCycle> cycles(long projectId) {
        return qtest.cycles(projectId);
    }

    @Override
    @WithoutTransaction
    public List<QTestRun> runs(long projectId, Long cycleId) {
        if (cycleId == null) {
            throw InvalidRequestException.of("cycleId", REQUIRED);
        }
        return qtest.runs(projectId, cycleId);
    }

    @Override
    @WithoutTransaction
    public QTestSummary summary(long projectId, long cycleId) {
        return qtest.summary(projectId, cycleId);
    }
}
