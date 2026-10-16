package com.bbh.itss.beadle.internal.adapter.out.http;

import lombok.NoArgsConstructor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@NoArgsConstructor(access = PRIVATE)
public final class ExternalSystems {

    public static final int MAX_REASON = 300;
    private static final DateTimeFormatter ATLASSIAN_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSZ");

    /**
     * A client for the system at {@code url} that sends {@code token} as its bearer token. The token is trimmed, as
     * the Beadle services compare the service token: a secret written from a file ends in a newline, and the HTTP
     * client refuses to send a header value that holds one.
     */
    public static RestClient client(RestClient.Builder builder, String url, String token) {
        return builder.baseUrl(removeEnd(trimToEmpty(url), "/"))
                .defaultHeader(AUTHORIZATION, "Bearer " + trimToEmpty(token)).build();
    }

    public static UncheckedIOException refusal(String system, String operation, RestClientException cause) {
        String reason = defaultIfBlank(cause.getMessage(), cause.getClass().getSimpleName());
        return new UncheckedIOException(system + " could not " + operation + ": " + truncate(reason, MAX_REASON),
                new IOException(cause));
    }

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
}
