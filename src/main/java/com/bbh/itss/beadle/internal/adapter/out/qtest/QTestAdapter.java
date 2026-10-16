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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.client;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.refusal;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.timestamp;
import static com.bbh.itss.beadle.internal.adapter.out.qtest.QTestProperties.CONFIGURED;
import static com.bbh.itss.beadle.internal.domain.qtest.QTestSummary.UNEXECUTED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.Strings.CI;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Reads qTest Manager through its v3 API. The runs of a cycle come 999 a page, the most qTest gives; their status
 * comes from the Status field of each run when qTest gives it, so that a summary needs no call per run, and the last
 * result of a run is read only where the time it was executed is wanted or the run has no status, at most
 * {@code beadle.qtest.concurrency} at once across all requests. A listing that takes longer than
 * {@code beadle.qtest.budget} is refused and its calls are interrupted, so that Beadle-GUI gets an answer before it
 * gives up waiting.
 */
@Component
@ConditionalOnExpression(CONFIGURED)
class QTestAdapter implements QTestPort {

    static final String QTEST = "qTest";
    static final String PROJECT = "qTest project";
    static final String CYCLE = "qTest test cycle";
    static final int PAGE_SIZE = 999;
    static final int MAX_RUNS = 2_000;
    static final String CYCLES_UNDER = "/api/v3/projects/{id}/test-cycles?parentId={parent}&parentType={type}"
            + "&expand=descendants";
    static final long ROOT_ID = 0;
    static final String ROOT = "root";
    static final String RELEASE = "release";
    static final String STATUS_FIELD = "Status";
    private static final ParameterizedTypeReference<List<Project>> PROJECTS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Cycle>> CYCLES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Release>> RELEASES = new ParameterizedTypeReference<>() {
    };

    private final RestClient client;
    private final Semaphore reads;
    private final Duration budget;

    QTestAdapter(QTestProperties properties, RestClient.Builder builder) {
        this.client = client(builder, properties.url(), properties.token());
        this.reads = new Semaphore(properties.concurrency(), true);
        this.budget = properties.budget();
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
        return withinBudget("list the test cycles of project " + projectId, () -> {
            List<QTestCycle> flat = new ArrayList<>();
            try {
                flatten(cyclesUnder(projectId, ROOT_ID, ROOT), flat);
            } catch (HttpClientErrorException.NotFound e) {
                throw notFound(PROJECT, projectId);
            } catch (RestClientException e) {
                throw refusal(QTEST, "list the test cycles of project " + projectId, e);
            }
            concurrently(releases(projectId), release -> releaseCycles(projectId, release))
                    .forEach(cycles -> flatten(cycles, flat));
            return flat;
        });
    }

    @Override
    public List<QTestRun> runs(long projectId, long cycleId) {
        return withinBudget("list the test runs of test cycle " + cycleId, () -> read(projectId, cycleId, false));
    }

    @Override
    public QTestSummary summary(long projectId, long cycleId) {
        return QTestSummary.of(withinBudget("count the test runs of test cycle " + cycleId,
                () -> read(projectId, cycleId, true)));
    }

    /**
     * The runs of a cycle with their status, and with the time of their last result unless only the status is
     * wanted.
     */
    private List<QTestRun> read(long projectId, long cycleId, boolean statusOnly) {
        List<Run> runs = runsOf(projectId, cycleId);
        List<Run> unread = runs.stream().filter(run -> run.needsLastResult(statusOnly)).toList();
        List<Log> logs = concurrently(unread, run -> lastLog(projectId, run.id()));
        Map<Long, Log> lastResults = new HashMap<>();
        for (int index = 0; index < unread.size(); index++) {
            lastResults.put(unread.get(index).id(), logs.get(index));
        }
        return runs.stream().map(run -> run.toRun(lastResults.get(run.id()))).toList();
    }

    private List<Cycle> cyclesUnder(long projectId, long parentId, String parentType) {
        return client.get().uri(CYCLES_UNDER, projectId, parentId, parentType).accept(APPLICATION_JSON).retrieve()
                .body(CYCLES);
    }

    private List<Cycle> releaseCycles(long projectId, Release release) {
        try {
            return cyclesUnder(projectId, release.id(), RELEASE);
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();  // a release deleted since it was listed has no cycles left
        } catch (RestClientException e) {
            throw refusal(QTEST, "list the test cycles of release " + release.id(), e);
        }
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

    /**
     * Runs {@code work} on its own thread and refuses it once it takes longer than the budget, interrupting the calls
     * it still has under way.
     */
    private <T> T withinBudget(String operation, Callable<T> work) {
        FutureTask<T> task = new FutureTask<>(work);
        Thread.ofVirtual().name("qtest-read").start(task);
        try {
            return task.get(budget.toNanos(), NANOSECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new UncheckedIOException(QTEST + " could not " + operation + " within " + seconds(budget)
                    + ": it answers too slowly or the listing is too large", new IOException(e));
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(QTEST + " could not " + operation + ": interrupted",
                    new InterruptedIOException(operation));
        } catch (ExecutionException e) {
            throw unwrapped(e);
        }
    }

    /**
     * Reads every item on a virtual thread of its own, at most {@code beadle.qtest.concurrency} at once across all
     * requests, and answers in the order of the items. The first failure fails all, and the reads still under way
     * are interrupted, as they are when the thread waiting for them is.
     */
    private <T, R> List<R> concurrently(List<T> items, Function<T, R> read) {
        if (items.isEmpty()) {
            return List.of();
        }
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<R>> futures = new ArrayList<>(items.size());
            for (T item : items) {
                futures.add(executor.submit(() -> withPermit(item, read)));
            }
            List<R> results = new ArrayList<>(items.size());
            for (Future<R> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new InterruptedIOException("the qTest reads were interrupted"));
        } catch (ExecutionException e) {
            throw unwrapped(e);
        } finally {
            executor.shutdownNow();
        }
    }

    private <T, R> R withPermit(T item, Function<T, R> read) throws InterruptedException {
        reads.acquire();
        try {
            return read.apply(item);
        } finally {
            reads.release();
        }
    }

    private static RuntimeException unwrapped(ExecutionException e) {
        if (e.getCause() instanceof RuntimeException runtime) {
            return runtime;
        }
        if (e.getCause() instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(e.getCause());
    }

    private static String seconds(Duration duration) {
        return duration.toMillis() % 1_000 == 0 ? duration.toSeconds() + " s" : duration.toMillis() + " ms";
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

    /**
     * A test run of the list, with the fields qTest gives it, among them its Status: the result of its last
     * execution, or Unexecuted.
     */
    record Run(long id, String name, String pid, List<Field> properties) {

        String status() {
            if (properties == null) {
                return null;
            }
            return properties.stream().filter(field -> CI.equals(field.name(), STATUS_FIELD))
                    .map(Field::valueName).filter(value -> !isBlank(value)).findFirst().orElse(null);
        }

        /**
         * Whether the last result must be read: for a run without a status, and, unless only the status is wanted,
         * for a run that was executed, to know when.
         */
        boolean needsLastResult(boolean statusOnly) {
            String status = status();
            return status == null || !statusOnly && !CI.equals(status, UNEXECUTED);
        }

        QTestRun toRun(Log log) {
            String status = log != null && log.status() != null && !isBlank(log.status().name())
                    ? log.status().name() : status() != null ? status() : UNEXECUTED;
            return new QTestRun(id, name, pid, status, log == null ? null : log.executedAt());
        }
    }

    record Field(@JsonProperty("field_name") String name, @JsonProperty("field_value_name") String valueName) {
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
