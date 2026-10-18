package com.bbh.itss.beadle.internal.adapter.out.confluence;

import com.bbh.itss.beadle.internal.adapter.out.http.ConditionalOnSystemUrl;
import com.bbh.itss.beadle.internal.adapter.out.http.SystemClient;
import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate;
import com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage;
import com.bbh.itss.beadle.internal.domain.shared.Failures;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.bbh.itss.beadle.internal.adapter.out.http.SystemClient.thrown;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

/**
 * Confluence through its REST API ({@code /rest/api/content}) with the bearer token of the Beadle service account.
 */
@Component
@ConditionalOnSystemUrl("confluence")
class ConfluenceAdapter implements ConfluencePort {

    static final String EXPAND = "?expand=space,version,body.storage";
    static final int LIMIT = 50;
    static final String NO_CONTENT = "Confluence answered without the page";

    private final SystemClient confluence;

    ConfluenceAdapter(ConfluenceProperties properties, RestClient.Builder builder) {
        this.confluence = new SystemClient("Confluence", builder, properties.url(), properties.token());
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public List<ConfluencePage> find(String spaceKey, String title) {
        Results results = confluence.call("find the page " + title + " in space " + spaceKey, client -> client.get()
                .uri("/rest/api/content?type=page&spaceKey={space}&title={title}&expand=space,version&limit={limit}",
                        spaceKey, title, LIMIT)
                .retrieve().body(Results.class));
        return results == null ? List.of()
                : emptyIfNull(results.results()).stream().map(this::toPage).toList();
    }

    @Override
    public ConfluencePage get(String id) {
        return page("read the page " + id, Map.of(404, thrown(() -> notFound(ConfluencePage.NAME, id))),
                client -> client.get().uri("/rest/api/content/{id}" + EXPAND, id));
    }

    @Override
    public ConfluencePage create(NewConfluencePage page) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("type", "page", "title", page.title(),
                "space", Map.of("key", page.spaceKey()), "body", storage(page.body())));
        if (page.parentId() != null) {
            body.put("ancestors", List.of(Map.of("id", page.parentId())));
        }
        return page("create the page " + page.title() + " in space " + page.spaceKey(), Map.of(),
                client -> client.post().uri("/rest/api/content" + EXPAND).body(body));
    }

    @Override
    public ConfluencePage update(String id, ConfluencePageUpdate update) {
        Map<String, Object> body = Map.of("id", id, "type", "page", "title", update.title(),
                "version", Map.of("number", update.version() + 1), "body", storage(update.body()));
        return page("update the page " + id, Map.of(404, thrown(() -> notFound(ConfluencePage.NAME, id)),
                        409, thrown(Failures::staleVersion)),
                client -> client.put().uri("/rest/api/content/{id}" + EXPAND, id).body(body));
    }

    private ConfluencePage page(String operation, Map<Integer, Supplier<ConfluencePage>> answers,
                                Function<RestClient, RestClient.RequestHeadersSpec<?>> request) {
        return confluence.call(operation, answers, client -> {
            Content content = request.apply(client).retrieve().body(Content.class);
            if (content == null || content.id() == null) {
                throw new RestClientException(NO_CONTENT);
            }
            return toPage(content);
        });
    }

    private static Map<String, Object> storage(String body) {
        return Map.of("storage", Map.of("value", body, "representation", "storage"));
    }

    private ConfluencePage toPage(Content content) {
        String webui = content.links() == null ? null : content.links().get("webui");
        return new ConfluencePage(content.id(), content.space() == null ? null : content.space().key(),
                content.title(), content.version() == null ? 0 : content.version().number(),
                webui == null ? null : confluence.url() + webui,
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
