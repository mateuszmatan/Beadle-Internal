package com.bbh.itss.dso.portal.adapter.out.jira;

import com.bbh.itss.dso.portal.application.change.port.out.CyberTrackPort;
import com.bbh.itss.dso.portal.domain.change.SecureCodingTicket;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

import static com.bbh.itss.dso.portal.adapter.out.jira.CyberTrackProperties.CONFIGURED;
import static java.time.Duration.ofSeconds;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
@ConditionalOnExpression(CONFIGURED)
class JiraCyberTrackAdapter implements CyberTrackPort {

    static final String REFUSED = "CyberTrack could not create the secure coding ticket: ";
    static final String NO_KEY = "Jira answered without the key of the new issue";
    private static final int MAX_REASON = 300;

    private final CyberTrackProperties properties;
    private final RestClient client;

    JiraCyberTrackAdapter(CyberTrackProperties properties, RestClient.Builder builder) {
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
    public String create(SecureCodingTicket ticket) {
        Map<String, Object> fields = Map.of(
                "project", Map.of("key", properties.projectKey()),
                "issuetype", Map.of("name", properties.issueType()),
                "summary", ticket.summary(),
                "description", ticket.description());
        try {
            Created created = client.post().uri("/rest/api/2/issue").contentType(APPLICATION_JSON)
                    .accept(APPLICATION_JSON).body(Map.of("fields", fields)).retrieve().body(Created.class);
            return Optional.ofNullable(created).map(Created::key).filter(StringUtils::isNotBlank)
                    .orElseThrow(() -> new RestClientException(NO_KEY));
        } catch (RestClientException e) {
            throw new UncheckedIOException(REFUSED + truncate(e.getMessage(), MAX_REASON), new IOException(e));
        }
    }

    record Created(String key) {
    }
}
