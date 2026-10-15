package com.bbh.itss.beadle.internal.adapter.out.cybertrack;

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackProperties.CONFIGURED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static java.time.Duration.ofSeconds;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
@ConditionalOnExpression(CONFIGURED)
class CyberTrackAdapter implements CyberTrackPort {

    static final String REFUSED = "CyberTrack could not create the secure coding ticket: ";
    static final String UNREADABLE = "CyberTrack could not read the ticket: ";
    static final String NO_KEY = "Jira answered without the key of the new issue";
    static final String TICKET = "CyberTrack ticket";
    private static final int MAX_REASON = 300;
    private static final DateTimeFormatter JIRA_INSTANT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSZ");

    private final CyberTrackProperties properties;
    private final RestClient client;

    CyberTrackAdapter(CyberTrackProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ofSeconds(5));
        requestFactory.setReadTimeout(ofSeconds(30));
        this.properties = properties;
        this.client = builder.baseUrl(properties.url()).requestFactory(requestFactory)
                .defaultHeader(AUTHORIZATION, "Bearer " + defaultString(properties.token())).build();
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public CreatedTicket create(SecureCodingTicket ticket) {
        Map<String, Object> fields = Map.of(
                "project", Map.of("key", properties.projectKey()),
                "issuetype", Map.of("name", properties.issueType()),
                "summary", ticket.summary(),
                "description", ticket.description());
        try {
            Created created = client.post().uri("/rest/api/2/issue").contentType(APPLICATION_JSON)
                    .accept(APPLICATION_JSON).body(Map.of("fields", fields)).retrieve().body(Created.class);
            String key = Optional.ofNullable(created).map(Created::key).filter(StringUtils::isNotBlank)
                    .orElseThrow(() -> new RestClientException(NO_KEY));
            return new CreatedTicket(key, browseUrl(key));
        } catch (RestClientException e) {
            throw new UncheckedIOException(REFUSED + truncate(e.getMessage(), MAX_REASON), new IOException(e));
        }
    }

    @Override
    public CyberTrackTicket read(String key) {
        try {
            Issue issue = client.get().uri("/rest/api/2/issue/{key}?fields=status,updated", key)
                    .accept(APPLICATION_JSON).retrieve().body(Issue.class);
            if (issue == null || issue.fields() == null) {
                throw new RestClientException("Jira answered without the fields of " + key);
            }
            String status = Optional.ofNullable(issue.fields().status()).map(Status::name).orElse(null);
            return new CyberTrackTicket(key, status, browseUrl(key), instant(issue.fields().updated()));
        } catch (HttpClientErrorException.NotFound e) {
            throw notFound(TICKET, key);
        } catch (RestClientException e) {
            throw new UncheckedIOException(UNREADABLE + truncate(e.getMessage(), MAX_REASON), new IOException(e));
        }
    }

    private String browseUrl(String key) {
        return removeEnd(properties.url(), "/") + "/browse/" + key;
    }

    static Instant instant(String jiraTimestamp) {
        if (jiraTimestamp == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(jiraTimestamp, ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(jiraTimestamp, JIRA_INSTANT).toInstant();
        }
    }

    record Created(String key) {
    }

    record Issue(String key, Fields fields) {
    }

    record Fields(Status status, String updated) {
    }

    record Status(String name) {
    }
}
