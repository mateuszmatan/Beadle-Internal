package com.bbh.itss.beadle.internal.application.jira.port.in;

import com.bbh.itss.beadle.internal.domain.jira.JiraIssue;
import com.bbh.itss.beadle.internal.domain.jira.JiraVersion;

import java.util.List;

public interface JiraUseCase {

    List<JiraVersion> versions(String project);

    List<JiraIssue> epics(String project, String fixVersion);

    List<JiraIssue> stories(String project, String fixVersion, List<String> epicKeys);
}
