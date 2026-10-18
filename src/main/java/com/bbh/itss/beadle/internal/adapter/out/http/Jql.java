package com.bbh.itss.beadle.internal.adapter.out.http;

import lombok.NoArgsConstructor;

import java.util.Collection;

import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PRIVATE;

/**
 * The JQL that Jira and CyberTrack (a Jira of its own) are searched with.
 */
@NoArgsConstructor(access = PRIVATE)
public final class Jql {

    public static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static String list(Collection<String> values) {
        return values.stream().map(Jql::quoted).collect(joining(", ", "(", ")"));
    }
}
