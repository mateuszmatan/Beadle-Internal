package com.bbh.itss.beadle.internal.adapter.out.qtest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Where qTest is and how hard Beadle may press it: at most {@code concurrency} reads at once across all requests for
 * the last results of runs and the cycles of releases, and an answer within {@code budget}, below the 30 seconds
 * Beadle-GUI waits for one.
 */
@Validated
@ConfigurationProperties("beadle.qtest")
public record QTestProperties(String url, String token, @Min(1) @Max(50) int concurrency,
                              @NotNull @DurationMin(millis = 1) @DurationMax(seconds = 30, inclusive = false)
                              Duration budget) {
}
