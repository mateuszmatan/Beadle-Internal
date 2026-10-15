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

import java.util.Map;
import java.util.Optional;

import static com.bbh.itss.beadle.internal.adapter.out.cybertrack.CyberTrackProperties.CONFIGURED;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.client;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.refusal;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.timestamp;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
@ConditionalOnExpression(CONFIGURED)
class CyberTrackAdapter implements CyberTrackPort {

    static final String CYBERTRACK = "CyberTrack";
    static final String CREATE = "create the secure coding ticket";
    static final String READ = "read the ticket";
    static final String NO_KEY = "Jira answered without the key of the new issue";
    static final String NO_FIELDS = "Jira answered without the fields of ";
    static final String TICKET = "CyberTrack ticket";

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
            String status = Optional.ofNullable(issue.fields().status()).map(Status::name).orElse(null);
            return new CyberTrackTicket(key, status, browseUrl(key), timestamp(issue.fields().updated()));
        } catch (HttpClientErrorException.NotFound e) {
            throw notFound(TICKET, key);
        } catch (RestClientException e) {
            throw refusal(CYBERTRACK, READ + " " + key, e);
        }
    }

    private String browseUrl(String key) {
        return removeEnd(properties.url().trim(), "/") + "/browse/" + key;
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
