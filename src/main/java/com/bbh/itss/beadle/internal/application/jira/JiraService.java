package com.bbh.itss.beadle.internal.application.jira;

import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.WithoutTransaction;
import com.bbh.itss.beadle.internal.application.jira.port.in.JiraUseCase;
import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort;
import com.bbh.itss.beadle.internal.domain.jira.JiraIssue;
import com.bbh.itss.beadle.internal.domain.jira.JiraVersion;
import com.bbh.itss.beadle.internal.domain.shared.InvalidRequestException;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.bbh.itss.beadle.internal.domain.jira.JiraVersion.UNRELEASED_NEWEST_FIRST;
import static com.bbh.itss.beadle.internal.domain.shared.Text.clean;
import static com.bbh.itss.beadle.internal.domain.shared.ValidationProblems.REQUIRED;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@UseCase
@RequiredArgsConstructor
public class JiraService implements JiraUseCase {

    private final JiraPort jira;

    @Override
    @WithoutTransaction
    public List<JiraVersion> versions(String project) {
        return jira.versions(required("key", project)).stream().sorted(UNRELEASED_NEWEST_FIRST).toList();
    }

    @Override
    @WithoutTransaction
    public List<JiraIssue> epics(String project, String fixVersion) {
        return jira.epics(required("key", project), required("fixVersion", fixVersion));
    }

    @Override
    @WithoutTransaction
    public List<JiraIssue> stories(String project, String fixVersion, List<String> epicKeys) {
        String key = required("key", project);
        String version = required("fixVersion", fixVersion);
        List<String> keys = clean(epicKeys);
        return keys.isEmpty() ? List.of() : jira.stories(key, version, keys);
    }

    static String required(String field, String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw InvalidRequestException.of(field, REQUIRED);
        }
        return trimmed;
    }
}
