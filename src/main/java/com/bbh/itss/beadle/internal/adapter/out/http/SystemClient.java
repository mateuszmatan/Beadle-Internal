package com.bbh.itss.beadle.internal.adapter.out.http;

import com.bbh.itss.beadle.internal.domain.shared.RefusedException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.apache.commons.lang3.Strings.CS;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * One system Beadle-Internal calls over REST with the bearer token of its generic service account (or the Beadle
 * service token for Beadle-GUI). The URL and the token are trimmed: a secret written from a file ends in a newline,
 * which the HTTP client refuses to send. Unless the caller says what an HTTP status means (a {@code 404} for a key
 * the system does not know, for example), a call the system refuses (a {@code 4xx} other than a failed sign-in, a
 * time-out or a throttle) fails with a {@link RefusedException}, which the API answers {@code 409}, and a call that
 * does not reach the system or that it fails with an {@link UncheckedIOException}, answered {@code 502}; both name
 * the system and the operation.
 */
@Slf4j
public final class SystemClient {

    public static final int MAX_REASON = 300;
    private static final Set<Integer> UNAVAILABLE = Set.of(401, 403, 407, 408, 429);
    private static final DateTimeFormatter ATLASSIAN_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSZ");

    @Getter
    private final String name;
    @Getter
    private final String url;
    private final RestClient client;

    public SystemClient(String name, RestClient.Builder builder, String url, String token) {
        this.name = name;
        this.url = CS.removeEnd(trimToEmpty(url), "/");
        this.client = builder.baseUrl(this.url).defaultHeader(AUTHORIZATION, "Bearer " + trimToEmpty(token))
                .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE).build();
    }

    public <T> T call(String operation, Function<RestClient, T> request) {
        return call(operation, Map.of(), request);
    }

    /**
     * Makes one call; {@code operation} says what was asked ("read the ticket SCP-1"), {@code answers} what an HTTP
     * status means instead of a refusal.
     */
    public <T> T call(String operation, Map<Integer, Supplier<T>> answers, Function<RestClient, T> request) {
        long started = System.nanoTime();
        try {
            T answer = request.apply(client);
            log.debug("{} answered to {} in {} ms", name, operation, millisSince(started));
            return answer;
        } catch (RestClientResponseException e) {
            Supplier<T> answer = answers.get(e.getStatusCode().value());
            if (answer == null) {
                throw refusal(operation, e.getStatusCode().value(), e, started);
            }
            log.debug("{} answered {} to {} in {} ms", name, e.getStatusCode().value(), operation, millisSince(started));
            return answer.get();
        } catch (RestClientException e) {
            throw refusal(operation, null, e, started);
        }
    }

    /**
     * What an HTTP status means when it means a failure of its own, as {@code thrown(() -> notFound(...))}.
     */
    public static <T> Supplier<T> thrown(Supplier<? extends RuntimeException> failure) {
        return () -> {
            throw failure.get();
        };
    }

    /**
     * An instant as Jira, Confluence and qTest write it, ISO 8601 with or without the colon in the offset.
     */
    public static Instant timestamp(String text) {
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text, ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(text, ATLASSIAN_TIMESTAMP).toInstant();
        }
    }

    private RuntimeException refusal(String operation, Integer status, RestClientException cause, long started) {
        String reason = truncate(defaultIfBlank(cause.getMessage(), cause.getClass().getSimpleName()), MAX_REASON);
        log.warn("{} failed to {} with status {} after {} ms: {}", name, operation, status, millisSince(started), reason);
        if (cause instanceof RestClientResponseException response && response.getStatusCode().is4xxClientError()
                && !UNAVAILABLE.contains(status)) {
            return new RefusedException(name + " refused to " + operation + ": " + reason);
        }
        return new UncheckedIOException(name + " could not " + operation + ": " + reason, new IOException(cause));
    }

    private static long millisSince(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
