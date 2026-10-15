package com.bbh.itss.beadle.internal.adapter.out.confluence;

import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate;
import com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.beadle.internal.adapter.out.confluence.ConfluenceProperties.CONFIGURED;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.client;
import static com.bbh.itss.beadle.internal.adapter.out.http.ExternalSystems.refusal;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.staleVersion;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
@ConditionalOnExpression(CONFIGURED)
class ConfluenceAdapter implements ConfluencePort {

    static final String CONFLUENCE = "Confluence";
    static final String PAGE = "Confluence page";
    static final String EXPAND = "space,version,body.storage";
    static final int LIMIT = 50;
    static final String NO_CONTENT = "Confluence answered without the page";

    private final ConfluenceProperties properties;
    private final RestClient client;

    ConfluenceAdapter(ConfluenceProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = client(builder, properties.url(), properties.token());
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public List<ConfluencePage> find(String spaceKey, String title) {
        try {
            Results results = client.get()
                    .uri("/rest/api/content?type=page&spaceKey={space}&title={title}&expand=space,version&limit={limit}",
                            spaceKey, title, LIMIT)
                    .accept(APPLICATION_JSON).retrieve().body(Results.class);
            return results == null || results.results() == null ? List.of()
                    : results.results().stream().map(this::toPage).map(ConfluencePage::withoutBody).toList();
        } catch (RestClientException e) {
            throw refusal(CONFLUENCE, "find the page " + title + " in space " + spaceKey, e);
        }
    }

    @Override
    public ConfluencePage get(String id) {
        try {
            return toPage(existing(client.get().uri("/rest/api/content/{id}?expand=" + EXPAND, id)
                    .accept(APPLICATION_JSON).retrieve().body(Content.class)));
        } catch (HttpClientErrorException.NotFound e) {
            throw notFound(PAGE, id);
        } catch (RestClientException e) {
            throw refusal(CONFLUENCE, "read the page " + id, e);
        }
    }

    @Override
    public ConfluencePage create(NewConfluencePage page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "page");
        body.put("title", page.title());
        body.put("space", Map.of("key", page.spaceKey()));
        if (page.parentId() != null) {
            body.put("ancestors", List.of(Map.of("id", page.parentId())));
        }
        body.put("body", storage(page.body()));
        try {
            return toPage(existing(client.post().uri("/rest/api/content?expand=" + EXPAND)
                    .contentType(APPLICATION_JSON).accept(APPLICATION_JSON).body(body).retrieve()
                    .body(Content.class)));
        } catch (RestClientException e) {
            throw refusal(CONFLUENCE, "create the page " + page.title() + " in space " + page.spaceKey(), e);
        }
    }

    @Override
    public ConfluencePage update(String id, ConfluencePageUpdate update) {
        Map<String, Object> body = Map.of(
                "id", id,
                "type", "page",
                "title", update.title(),
                "version", Map.of("number", update.version() + 1),
                "body", storage(update.body()));
        try {
            return toPage(existing(client.put().uri("/rest/api/content/{id}?expand=" + EXPAND, id)
                    .contentType(APPLICATION_JSON).accept(APPLICATION_JSON).body(body).retrieve()
                    .body(Content.class)));
        } catch (HttpClientErrorException.NotFound e) {
            throw notFound(PAGE, id);
        } catch (HttpClientErrorException.Conflict e) {
            throw staleVersion();
        } catch (RestClientException e) {
            throw refusal(CONFLUENCE, "update the page " + id, e);
        }
    }

    private static Map<String, Object> storage(String body) {
        return Map.of("storage", Map.of("value", body, "representation", "storage"));
    }

    private static Content existing(Content content) {
        if (content == null || content.id() == null) {
            throw new RestClientException(NO_CONTENT);
        }
        return content;
    }

    private ConfluencePage toPage(Content content) {
        String webui = content.links() == null ? null : content.links().get("webui");
        return new ConfluencePage(content.id(), content.space() == null ? null : content.space().key(),
                content.title(), content.version() == null ? 0 : content.version().number(),
                webui == null ? null : removeEnd(properties.url().trim(), "/") + webui,
                content.body() == null || content.body().storage() == null ? null : content.body().storage().value());
    }

    record Results(List<Content> results) {
    }

    record Content(String id, String title, Space space, Version version, Body body,
                   @JsonProperty("_links") Map<String, String> links) {
    }

    record Space(String key) {
    }

    record Version(int number) {
    }

    record Body(Storage storage) {
    }

    record Storage(String value) {
    }
}
