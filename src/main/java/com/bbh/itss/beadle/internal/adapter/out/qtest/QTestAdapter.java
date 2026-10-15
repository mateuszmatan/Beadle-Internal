package com.bbh.itss.beadle.internal.adapter.out.qtest;

import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort;
import com.bbh.itss.beadle.internal.domain.qtest.QTestCycle;
import com.bbh.itss.beadle.internal.domain.qtest.QTestProject;
import com.bbh.itss.beadle.internal.domain.qtest.QTestRun;
import com.bbh.itss.beadle.internal.domain.qtest.QTestSummary;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.client;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.refusal;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.timestamp;
import static com.bbh.itss.beadle.internal.adapter.out.qtest.QTestProperties.CONFIGURED;
import static com.bbh.itss.beadle.internal.domain.qtest.QTestSummary.UNEXECUTED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
@ConditionalOnExpression(CONFIGURED)
class QTestAdapter implements QTestPort {

    static final String QTEST = "qTest";
    static final String PROJECT = "qTest project";
    static final String CYCLE = "qTest test cycle";
    static final int PAGE_SIZE = 100;
    static final int MAX_RUNS = 2_000;
    private static final ParameterizedTypeReference<List<Project>> PROJECTS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Cycle>> CYCLES = new ParameterizedTypeReference<>() {
    };

    private final RestClient client;

    QTestAdapter(QTestProperties properties, RestClient.Builder builder) {
        this.client = client(builder, properties.url(), properties.token());
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public List<QTestProject> projects() {
        try {
            List<Project> projects = client.get().uri("/api/v3/projects").accept(APPLICATION_JSON).retrieve()
                    .body(PROJECTS);
            return projects == null ? List.of()
                    : projects.stream().map(project -> new QTestProject(project.id(), project.name())).toList();
        } catch (RestClientException e) {
            throw refusal(QTEST, "list the projects", e);
        }
    }

    @Override
    public List<QTestCycle> cycles(long projectId) {
        try {
            List<Cycle> cycles = client.get().uri("/api/v3/projects/{id}/test-cycles", projectId)
                    .accept(APPLICATION_JSON).retrieve().body(CYCLES);
            List<QTestCycle> flat = new ArrayList<>();
            flatten(cycles, flat);
            return flat;
        } catch (HttpClientErrorException.NotFound e) {
            throw notFound(PROJECT, projectId);
        } catch (RestClientException e) {
            throw refusal(QTEST, "list the test cycles of project " + projectId, e);
        }
    }

    @Override
    public List<QTestRun> runs(long projectId, long cycleId) {
        List<Run> runs = runsOf(projectId, cycleId);
        List<QTestRun> result = new ArrayList<>();
        for (Run run : runs) {
            Log log = lastLog(projectId, run.id());
            result.add(new QTestRun(run.id(), run.name(), run.pid(),
                    log == null || log.status() == null || log.status().name() == null ? UNEXECUTED : log.status().name(),
                    log == null ? null : timestamp(log.executionDate())));
        }
        return result;
    }

    @Override
    public QTestSummary summary(long projectId, long cycleId) {
        return QTestSummary.of(runs(projectId, cycleId));
    }

    private List<Run> runsOf(long projectId, long cycleId) {
        try {
            client.get().uri("/api/v3/projects/{id}/test-cycles/{cycleId}", projectId, cycleId)
                    .accept(APPLICATION_JSON).retrieve().body(Cycle.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw notFound(CYCLE, cycleId);
        } catch (RestClientException e) {
            throw refusal(QTEST, "read the test cycle " + cycleId, e);
        }
        List<Run> runs = new ArrayList<>();
        try {
            int total;
            int page = 0;
            do {
                RunPage fetched = client.get()
                        .uri("/api/v3/projects/{id}/test-runs?parentId={cycle}&parentType=test-cycle"
                                + "&expand=descendants&page={page}&pageSize={size}",
                                projectId, cycleId, ++page, PAGE_SIZE)
                        .accept(APPLICATION_JSON).retrieve().body(RunPage.class);
                if (fetched == null || fetched.items() == null || fetched.items().isEmpty()) {
                    break;
                }
                runs.addAll(fetched.items());
                total = fetched.total();
            } while (runs.size() < total && runs.size() < MAX_RUNS);
        } catch (RestClientException e) {
            throw refusal(QTEST, "list the test runs of test cycle " + cycleId, e);
        }
        return runs;
    }

    private Log lastLog(long projectId, long runId) {
        try {
            return client.get().uri("/api/v3/projects/{id}/test-runs/{run}/test-logs/last-run", projectId, runId)
                    .accept(APPLICATION_JSON).retrieve().body(Log.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw refusal(QTEST, "read the last result of test run " + runId, e);
        }
    }

    private static void flatten(List<Cycle> cycles, List<QTestCycle> flat) {
        if (cycles == null) {
            return;
        }
        for (Cycle cycle : cycles) {
            flat.add(new QTestCycle(cycle.id(), cycle.name(), cycle.pid()));
            flatten(cycle.children(), flat);
        }
    }

    record Project(long id, String name) {
    }

    record Cycle(long id, String name, String pid, @JsonProperty("test-cycles") List<Cycle> children) {
    }

    record RunPage(int page, int pageSize, int total, List<Run> items) {
    }

    record Run(long id, String name, String pid) {
    }

    record Log(Status status, @JsonProperty("execution_date") String executionDate) {
    }

    record Status(String name) {
    }
}
