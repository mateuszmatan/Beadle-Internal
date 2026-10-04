package com.bbh.itss.dso.portal.support

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LatencyStats {

    final String name
    final List<Double> millis
    final double wallSeconds
    final int errors

    private LatencyStats(String name, List<Double> millis, double wallSeconds, int errors) {
        this.name = name
        this.millis = millis.sort()
        this.wallSeconds = wallSeconds
        this.errors = errors
    }

    static LatencyStats measure(Map options, String name, Closure<Boolean> call) {
        int calls = options.calls as int
        int threads = (options.threads ?: 1) as int
        def pool = Executors.newFixedThreadPool(threads)
        try {
            if (options.warmUp != false) {
                pool.invokeAll((0..<Math.min(threads, calls)).collect { int index -> { -> call(index) } as Callable })*.get()
            }
            long start = System.nanoTime()
            def futures = pool.invokeAll((0..<calls).collect { int index ->
                { ->
                    long begin = System.nanoTime()
                    boolean ok = call(index)
                    [(System.nanoTime() - begin) / 1_000_000d, ok]
                } as Callable<List>
            })
            def results = futures*.get()
            double wall = (System.nanoTime() - start) / 1_000_000_000d
            new LatencyStats(name, results.collect { it[0] as double }, wall, results.count { !it[1] } as int)
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    double percentile(double p) {
        millis[Math.min(millis.size() - 1, Math.ceil(p / 100 * millis.size()) as int - 1)]
    }

    double getP50() {
        percentile(50)
    }

    double getP95() {
        percentile(95)
    }

    double getMax() {
        millis.last()
    }

    double getThroughput() {
        millis.size() / wallSeconds
    }

    String toRow() {
        String.format('| %-40s | %6d | %8.1f | %8.1f | %8.1f | %9.1f | %6d |', name, millis.size(), p50, p95, max,
                throughput, errors)
    }

    static String header() {
        '| Scenario                                 |  Calls | p50 (ms) | p95 (ms) | max (ms) | calls / s | Errors |\n' +
                '|------------------------------------------|--------|----------|----------|----------|-----------|--------|'
    }
}
