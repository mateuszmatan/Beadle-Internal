package com.bbh.itss.beadle.internal.adapter.out.cybertrack;

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackProperties.CONFIGURED;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.client;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.refusal;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.timestamp;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static java.lang.Math.min;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
@ConditionalOnExpression(CONFIGURED)
class CyberTrackAdapter implements CyberTrackPort {

    static final String CYBERTRACK = "CyberTrack";
    static final String CREATE = "create the secure coding ticket";
    static final String READ = "read the ticket";
    static final String READ_ALL = "read the tickets";
    static final String NO_KEY = "Jira answered without the key of the new issue";
    static final String NO_FIELDS = "Jira answered without the fields of ";
    static final String TICKET = "CyberTrack ticket";
    static final int BATCH = 50;
    static final int MAX_KEYS = 200;

    private final CyberTrackProperties properties;
    private final RestClient client;

    CyberTrackAdapter(CyberTrackProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = client(builder, properties.url(), properties.token());
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
            throw refusal(CYBERTRACK, CREATE, e);
        }
    }

    @Override
    public CyberTrackTicket read(String key) {
        try {
            Issue issue = client.get().uri("/rest/api/2/issue/{key}?fields=status,updated", key)
                    .accept(APPLICATION_JSON).retrieve().body(Issue.class);
            if (issue == null || issue.fields() == null) {
                throw new RestClientException(NO_FIELDS + key);
            }
            return ticket(key, issue.fields());
        } catch (HttpClientErrorException.NotFound e) {
            throw notFound(TICKET, key);
        } catch (RestClientException e) {
            throw refusal(CYBERTRACK, READ + " " + key, e);
        }
    }

    /**
     * Searches the tickets by key, {@value #BATCH} at a time, without validating the query so that a key Jira does
     * not know (a deleted issue) is left out instead of refusing the whole search.
     */
    @Override
    public List<CyberTrackTicket> readAll(Collection<String> keys) {
        List<String> distinct = keys.stream().distinct().toList();
        List<CyberTrackTicket> tickets = new ArrayList<>();
        try {
            for (int from = 0; from < distinct.size(); from += BATCH) {
                List<String> batch = distinct.subList(from, min(from + BATCH, distinct.size()));
                SearchResult result = client.post().uri("/rest/api/2/search").contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .body(Map.of("jql", "key in " + batch.stream().map(CyberTrackAdapter::quoted)
                                        .collect(joining(", ", "(", ")")),
                                "startAt", 0, "maxResults", batch.size(), "validateQuery", false,
                                "fields", List.of("status", "updated")))
                        .retrieve().body(SearchResult.class);
                if (result != null && result.issues() != null) {
                    result.issues().stream().filter(issue -> issue.key() != null && issue.fields() != null)
                            .map(issue -> ticket(issue.key(), issue.fields())).forEach(tickets::add);
                }
            }
        } catch (RestClientException e) {
            throw refusal(CYBERTRACK, READ_ALL + " " + abbreviate(String.join(", ", distinct), MAX_KEYS), e);
        }
        return tickets;
    }

    private CyberTrackTicket ticket(String key, Fields fields) {
        String status = Optional.ofNullable(fields.status()).map(Status::name).orElse(null);
        return new CyberTrackTicket(key, status, browseUrl(key), timestamp(fields.updated()));
    }

    private String browseUrl(String key) {
        return removeEnd(properties.url().trim(), "/") + "/browse/" + key;
    }

    static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    record Created(String key) {
    }

    record SearchResult(List<Issue> issues) {
    }

    record Issue(String key, Fields fields) {
    }

    record Fields(Status status, String updated) {
    }

    record Status(String name) {
    }
}
