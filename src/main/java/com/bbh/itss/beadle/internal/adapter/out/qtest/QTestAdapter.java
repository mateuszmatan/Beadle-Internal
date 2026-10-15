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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.client;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.refusal;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.timestamp;
import static com.bbh.itss.beadle.internal.adapter.out.qtest.QTestProperties.CONFIGURED;
import static com.bbh.itss.beadle.internal.domain.qtest.QTestSummary.UNEXECUTED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static org.apache.commons.lang3.StringUtils.isBlank;
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
    private static final ParameterizedTypeReference<List<Release>> RELEASES = new ParameterizedTypeReference<>() {
    };
    static final String CYCLES_UNDER = "/api/v3/projects/{id}/test-cycles?parentId={parent}&parentType={type}"
            + "&expand=descendants";
    static final long ROOT_ID = 0;
    static final String ROOT = "root";
    static final String RELEASE = "release";

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

    /**
     * The test cycles under the project root, then those of each release, each followed by the cycles nested in it.
     * qTest answers only the cycles right under the parent it is given unless asked for their descendants, and keeps
     * the cycles of a release under that release instead of the root.
     */
    @Override
    public List<QTestCycle> cycles(long projectId) {
        List<QTestCycle> flat = new ArrayList<>();
        try {
            flatten(cyclesUnder(projectId, ROOT_ID, ROOT), flat);
        } catch (HttpClientErrorException.NotFound e) {
            throw notFound(PROJECT, projectId);
        } catch (RestClientException e) {
            throw refusal(QTEST, "list the test cycles of project " + projectId, e);
        }
        for (Release release : releases(projectId)) {
            try {
                flatten(cyclesUnder(projectId, release.id(), RELEASE), flat);
            } catch (HttpClientErrorException.NotFound e) {
                // a release deleted since it was listed has no cycles left
            } catch (RestClientException e) {
                throw refusal(QTEST, "list the test cycles of release " + release.id(), e);
            }
        }
        return flat;
    }

    private List<Cycle> cyclesUnder(long projectId, long parentId, String parentType) {
        return client.get().uri(CYCLES_UNDER, projectId, parentId, parentType).accept(APPLICATION_JSON).retrieve()
                .body(CYCLES);
    }

    private List<Release> releases(long projectId) {
        try {
            List<Release> releases = client.get().uri("/api/v3/projects/{id}/releases", projectId)
                    .accept(APPLICATION_JSON).retrieve().body(RELEASES);
            return releases == null ? List.of() : releases;
        } catch (HttpClientErrorException.NotFound e) {
            throw notFound(PROJECT, projectId);
        } catch (RestClientException e) {
            throw refusal(QTEST, "list the releases of project " + projectId, e);
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
                    log == null ? null : log.executedAt()));
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
            boolean more;
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
                more = fetched.total() == null ? fetched.items().size() >= PAGE_SIZE : runs.size() < fetched.total();
            } while (more && runs.size() < MAX_RUNS);
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

    record Release(long id, String name) {
    }

    record Cycle(long id, String name, String pid, @JsonProperty("test-cycles") List<Cycle> children) {
    }

    record RunPage(Integer page, @JsonProperty("page_size") Integer pageSize, Integer total, List<Run> items) {
    }

    record Run(long id, String name, String pid) {
    }

    /**
     * The last result of a test run, with the ISO 8601 times its execution started and ended.
     */
    record Log(Status status, @JsonProperty("exe_start_date") String startedAt,
               @JsonProperty("exe_end_date") String endedAt) {

        /**
         * When the run was executed: the end of its last execution, or its start when it has no end. A time qTest
         * writes in another notation is left out rather than failing the list.
         */
        Instant executedAt() {
            String at = isBlank(endedAt) ? startedAt : endedAt;
            try {
                return isBlank(at) ? null : timestamp(at);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    record Status(String name) {
    }
}
