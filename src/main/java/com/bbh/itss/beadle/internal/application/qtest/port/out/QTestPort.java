package com.bbh.itss.beadle.internal.application.qtest.port.out;

import com.bbh.itss.beadle.internal.domain.qtest.QTestCycle;
import com.bbh.itss.beadle.internal.domain.qtest.QTestProject;
import com.bbh.itss.beadle.internal.domain.qtest.QTestRun;
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary;

import java.util.List;

public interface QTestPort {

    boolean connected();

    List<QTestProject> projects();

    List<QTestCycle> cycles(long projectId);

    List<QTestRun> runs(long projectId, long cycleId);

    QTestSummary summary(long projectId, long cycleId);
}
