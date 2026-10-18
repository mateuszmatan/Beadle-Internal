package com.bbh.itss.beadle.internal.support

import java.util.concurrent.Callable

import static java.lang.System.nanoTime
import static java.util.concurrent.Executors.newFixedThreadPool

/**
 * Makes a call {@code calls} times on {@code threads} threads after one warm-up call per thread, and keeps the
 * latencies and the calls that failed.
 */
class LatencyStats {

    final List<Double> millis
    final int errors

    private LatencyStats(List<Double> millis, int errors) {
        this.millis = millis.sort()
        this.errors = errors
    }

    static LatencyStats measure(int calls, int threads, Closure<Boolean> call) {
        def pool = newFixedThreadPool(threads)
        try {
            pool.invokeAll((1..threads).collect { { -> call() } as Callable })*.get()
            def results = pool.invokeAll((1..calls).collect {
                { ->
                    long started = nanoTime()
                    boolean ok = call()
                    [(nanoTime() - started) / 1_000_000d, ok]
                } as Callable<List>
            })*.get()
            new LatencyStats(results.collect { it[0] as double }, results.count { !it[1] } as int)
        } finally {
            pool.shutdownNow()
        }
    }

    double getP95() {
        millis[Math.ceil(0.95 * millis.size()) as int - 1]
    }
}
