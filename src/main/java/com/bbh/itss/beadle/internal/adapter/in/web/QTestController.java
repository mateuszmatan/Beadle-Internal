package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.application.qtest.port.in.QTestUseCase;
import com.bbh.itss.beadle.internal.domain.qtest.QTestCycle;
import com.bbh.itss.beadle.internal.domain.qtest.QTestProject;
import com.bbh.itss.beadle.internal.domain.qtest.QTestRun;
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/qtest/projects")
@RequiredArgsConstructor
public class QTestController {

    private final QTestUseCase qtest;

    @GetMapping
    public List<QTestProject> projects() {
        return qtest.projects();
    }

    @GetMapping("/{id}/test-cycles")
    public List<QTestCycle> cycles(@PathVariable long id) {
        return qtest.cycles(id);
    }

    @GetMapping("/{id}/test-runs")
    public List<QTestRun> runs(@PathVariable long id, @RequestParam(required = false) Long cycleId) {
        return qtest.runs(id, cycleId);
    }

    @GetMapping("/{id}/test-cycles/{cycleId}/summary")
    public QTestSummary summary(@PathVariable long id, @PathVariable long cycleId) {
        return qtest.summary(id, cycleId);
    }
}
