package com.bbh.itss.beadle.internal.adapter.out.cybertrack;

import com.bbh.itss.beadle.internal.adapter.out.http.ConditionalOnSystemUrl;
import com.bbh.itss.beadle.internal.adapter.out.http.SystemClient;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.bbh.itss.beadle.internal.adapter.out.http.Jql.list;
import static com.bbh.itss.beadle.internal.adapter.out.http.SystemClient.thrown;
import static com.bbh.itss.beadle.internal.adapter.out.http.SystemClient.timestamp;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.collections4.ListUtils.partition;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * CyberTrack, the Jira of the Cybersecurity team, through the Jira REST API v2 on its own URL and service account.
 */
@Component
@ConditionalOnSystemUrl("cybertrack")
class CyberTrackAdapter implements CyberTrackPort {

    static final String NO_KEY = "CyberTrack answered without the key of the new issue";
    static final String NO_FIELDS = "CyberTrack answered without the fields of ";
    static final int BATCH = 50;
    static final int MAX_KEYS = 200;

    private final CyberTrackProperties properties;
    private final SystemClient cyberTrack;

    CyberTrackAdapter(CyberTrackProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.cyberTrack = new SystemClient("CyberTrack", builder, properties.url(), properties.token());
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
        String key = cyberTrack.call("create the secure coding ticket", client -> {
            Issue created = client.post().uri("/rest/api/2/issue").body(Map.of("fields", fields)).retrieve()
                    .body(Issue.class);
            if (created == null || isBlank(created.key())) {
                throw new RestClientException(NO_KEY);
            }
            return created.key();
        });
        return new CreatedTicket(key, browseUrl(key));
    }

    @Override
    public CyberTrackTicket read(String key) {
        Fields fields = cyberTrack.call("read the ticket " + key,
                Map.of(404, thrown(() -> notFound(CyberTrackTicket.NAME, key))), client -> {
                    Issue issue = client.get().uri("/rest/api/2/issue/{key}?fields=status,updated", key).retrieve()
                            .body(Issue.class);
                    if (issue == null || issue.fields() == null) {
                        throw new RestClientException(NO_FIELDS + key);
                    }
                    return issue.fields();
                });
        return ticket(key, fields);
    }

    /**
     * Searches the tickets by key, {@value #BATCH} at a time, without validating the query so that a key CyberTrack
     * does not know (a deleted issue) is left out instead of refusing the whole search.
     */
    @Override
    public List<CyberTrackTicket> readAll(Collection<String> keys) {
        List<String> distinct = keys.stream().distinct().toList();
        String operation = "read the tickets " + abbreviate(String.join(", ", distinct), MAX_KEYS);
        return partition(distinct, BATCH).stream()
                .map(batch -> cyberTrack.call(operation, client -> client.post().uri("/rest/api/2/search")
                        .body(Map.of("jql", "key in " + list(batch), "startAt", 0, "maxResults", batch.size(),
                                "validateQuery", false, "fields", List.of("status", "updated")))
                        .retrieve().body(SearchResult.class)))
                .filter(Objects::nonNull).flatMap(result -> emptyIfNull(result.issues()).stream())
                .filter(issue -> issue.key() != null && issue.fields() != null)
                .map(issue -> ticket(issue.key(), issue.fields())).toList();
    }

    private CyberTrackTicket ticket(String key, Fields fields) {
        return new CyberTrackTicket(key, fields.status() == null ? null : fields.status().name(), browseUrl(key),
                timestamp(fields.updated()));
    }

    private String browseUrl(String key) {
        return cyberTrack.url() + "/browse/" + key;
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
