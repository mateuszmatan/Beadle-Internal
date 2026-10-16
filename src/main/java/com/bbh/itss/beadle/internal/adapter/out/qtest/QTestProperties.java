package com.bbh.itss.beadle.internal.adapter.out.qtest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Where qTest is and how hard Beadle may press it: at most {@code concurrency} reads at once across all requests for
 * the last results of runs and the cycles of releases, and an answer within {@code budget}, below the 30 seconds
 * Beadle-GUI waits for one.
 */
@ConfigurationProperties("beadle.qtest")
public record QTestProperties(String url, String token, int concurrency, Duration budget) {

    static final String CONFIGURED = "'${beadle.qtest.url:}' != ''";
    static final String NOT_CONFIGURED = "'${beadle.qtest.url:}' == ''";
    static final int CONCURRENCY = 8;
    static final int MAX_CONCURRENCY = 50;
    static final Duration BUDGET = Duration.ofSeconds(20);
    static final Duration GUI_WAIT = Duration.ofSeconds(30);

    @ConstructorBinding
    public QTestProperties(String url, String token, @DefaultValue("8") int concurrency,
                           @DefaultValue("20s") Duration budget) {
        if (concurrency < 1 || concurrency > MAX_CONCURRENCY) {
            throw new IllegalArgumentException("beadle.qtest.concurrency must be between 1 and " + MAX_CONCURRENCY
                    + ", not " + concurrency);
        }
        if (budget == null || budget.isNegative() || budget.isZero() || budget.compareTo(GUI_WAIT) >= 0) {
            throw new IllegalArgumentException("beadle.qtest.budget must be more than 0 and less than the "
                    + GUI_WAIT.toSeconds() + " seconds Beadle-GUI waits for an answer, not " + budget);
        }
        this.url = url;
        this.token = token;
        this.concurrency = concurrency;
        this.budget = budget;
    }

    QTestProperties(String url, String token) {
        this(url, token, CONCURRENCY, BUDGET);
    }
}
