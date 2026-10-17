package com.bbh.itss.beadle.internal.adapter.out.jira;

import com.bbh.itss.beadle.internal.adapter.out.http.SystemClient;
import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort;
import com.bbh.itss.beadle.internal.domain.jira.JiraIssue;
import com.bbh.itss.beadle.internal.domain.jira.JiraVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.bbh.itss.beadle.internal.adapter.out.http.Jql.list;
import static com.bbh.itss.beadle.internal.adapter.out.http.Jql.quoted;
import static com.bbh.itss.beadle.internal.adapter.out.http.SystemClient.thrown;
import static com.bbh.itss.beadle.internal.adapter.out.http.SystemClient.timestamp;
import static com.bbh.itss.beadle.internal.adapter.out.jira.JiraProperties.CONFIGURED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.collections4.ListUtils.partition;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * The BBH Jira of the delivery teams, REST API v2, with the bearer token of the Beadle service account.
 */
@Component
@ConditionalOnExpression(CONFIGURED)
class JiraAdapter implements JiraPort {

    static final int PAGE_SIZE = 100;
    static final int MAX_ISSUES = 1_000;
    static final int EPIC_BATCH = 100;
    static final String ORDER = " ORDER BY updated DESC, key ASC";
    private static final Comparator<Issue> NEWEST_FIRST = Comparator
            .comparing(Issue::updated, Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
            .thenComparing(Issue::key, JiraAdapter::keyOrder);
    private static final Pattern CUSTOM_FIELD = Pattern.compile("customfield_(\\d+)");
    private static final ParameterizedTypeReference<List<Version>> VERSIONS = new ParameterizedTypeReference<>() {
    };

    private final String epicLinkField;
    private final SystemClient jira;

    JiraAdapter(JiraProperties properties, RestClient.Builder builder) {
        this.epicLinkField = properties.epicLinkField();
        this.jira = new SystemClient("Jira", builder, properties.url(), properties.token());
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public List<JiraVersion> versions(String project) {
        List<Version> versions = jira.call("read the versions of " + project,
                Map.of(404, thrown(() -> notFound(JiraVersion.PROJECT, project))),
                client -> client.get().uri("/rest/api/2/project/{key}/versions", project).retrieve().body(VERSIONS));
        return emptyIfNull(versions).stream().map(Version::toVersion).toList();
    }

    /**
     * The epics of the project that carry the FixVersion or hold an issue that does, as the release notes count
     * them: an epic left without a FixVersion still comes with the stories it has in the release. The issues of the
     * FixVersion are searched first for the epics they belong to, then those epics with the ones that carry the
     * FixVersion, {@link #EPIC_BATCH} keys per search, without validating the keys, so that an epic deleted or out of
     * reach since is left out instead of failing the search.
     */
    @Override
    public List<JiraIssue> epics(String project, String fixVersion) {
        String operation = "read the epics of " + fixVersion;
        String inVersion = "fixVersion = " + quoted(fixVersion);
        String epics = "project = " + quoted(project) + " AND issuetype = Epic AND ";
        Set<String> linked = search("project = " + quoted(project) + " AND issuetype != Epic AND " + inVersion + ORDER,
                operation, true).stream().map(issue -> Issue.epicKey(issue.values(), epicLinkField))
                .filter(Objects::nonNull).collect(toCollection(LinkedHashSet::new));
        List<List<String>> batches = partition(new ArrayList<>(linked), EPIC_BATCH);
        if (batches.isEmpty()) {
            return search(epics + inVersion + ORDER, operation, true).stream().map(issue -> issue.toIssue(null))
                    .toList();
        }
        Map<String, Issue> found = new LinkedHashMap<>();
        for (int index = 0; index < batches.size(); index++) {
            String keys = "key in " + list(batches.get(index));
            String jql = epics + (index == 0 ? "(" + inVersion + " OR " + keys + ")" : keys) + ORDER;
            search(jql, operation, false).forEach(issue -> found.putIfAbsent(issue.key(), issue));
        }
        return found.values().stream().sorted(NEWEST_FIRST).map(issue -> issue.toIssue(null)).toList();
    }

    @Override
    public List<JiraIssue> stories(String project, String fixVersion, Collection<String> epicKeys) {
        String keys = list(epicKeys);
        String jql = "project = " + quoted(project) + " AND issuetype != Epic AND fixVersion = " + quoted(fixVersion)
                + " AND (parent in " + keys + " OR " + epicLinkClause() + " in " + keys + ")" + ORDER;
        return search(jql, "read the stories of " + fixVersion, true).stream()
                .map(issue -> issue.toIssue(epicLinkField)).toList();
    }

    private List<Issue> search(String jql, String operation, boolean validate) {
        List<Issue> issues = new ArrayList<>();
        int total;
        do {
            Map<String, Object> body = new LinkedHashMap<>(Map.of("jql", jql, "startAt", issues.size(),
                    "maxResults", PAGE_SIZE, "fields", List.of("summary", "status", "updated", "parent", epicLinkField)));
            if (!validate) {
                body.put("validateQuery", false);
            }
            SearchResult result = jira.call(operation, client -> client.post().uri("/rest/api/2/search").body(body)
                    .retrieve().body(SearchResult.class));
            if (result == null || emptyIfNull(result.issues()).isEmpty()) {
                break;
            }
            issues.addAll(result.issues());
            total = result.total();
        } while (issues.size() < total && issues.size() < MAX_ISSUES);
        return issues;
    }

    private String epicLinkClause() {
        Matcher custom = CUSTOM_FIELD.matcher(epicLinkField);
        return custom.matches() ? "cf[" + custom.group(1) + "]" : quoted(epicLinkField);
    }

    /**
     * The order of {@link #ORDER} for epics found by several searches: newest change first, then by key as Jira
     * orders keys, by project and then by number.
     */
    private static int keyOrder(String left, String right) {
        int leftDash = left.lastIndexOf('-');
        int rightDash = right.lastIndexOf('-');
        if (leftDash < 0 || rightDash < 0 || !left.substring(leftDash + 1).matches("\\d{1,18}")
                || !right.substring(rightDash + 1).matches("\\d{1,18}")) {
            return left.compareTo(right);
        }
        int project = left.substring(0, leftDash).compareTo(right.substring(0, rightDash));
        return project != 0 ? project : Long.compare(Long.parseLong(left.substring(leftDash + 1)),
                Long.parseLong(right.substring(rightDash + 1)));
    }

    record Version(String name, boolean released, String releaseDate) {

        JiraVersion toVersion() {
            return new JiraVersion(name, released, isBlank(releaseDate) ? null : LocalDate.parse(releaseDate));
        }
    }

    record SearchResult(int total, List<Issue> issues) {
    }

    record Issue(String key, Map<String, Object> fields) {

        Map<String, Object> values() {
            return fields == null ? Map.of() : fields;
        }

        Instant updated() {
            return values().get("updated") instanceof String updated ? timestamp(updated) : null;
        }

        JiraIssue toIssue(String epicLinkField) {
            Instant updated = updated();
            return new JiraIssue(key, (String) values().get("summary"), nameOf(values().get("status")),
                    epicLinkField == null ? null : epicKey(values(), epicLinkField),
                    updated == null ? null : updated.atZone(UTC).toLocalDate());
        }

        static String epicKey(Map<String, Object> values, String epicLinkField) {
            Object link = values.get(epicLinkField);
            if (link instanceof String key && !key.isBlank()) {
                return key;
            }
            String linked = keyOf(link);
            return linked != null ? linked : keyOf(values.get("parent"));
        }

        private static String nameOf(Object value) {
            return value instanceof Map<?, ?> map && map.get("name") instanceof String name ? name : null;
        }

        private static String keyOf(Object value) {
            return value instanceof Map<?, ?> map && map.get("key") instanceof String key ? key : null;
        }
    }
}
