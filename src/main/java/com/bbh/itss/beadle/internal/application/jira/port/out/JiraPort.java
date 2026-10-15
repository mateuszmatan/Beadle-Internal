package com.bbh.itss.beadle.internal.application.jira.port.out;

import com.bbh.itss.beadle.internal.domain.jira.JiraIssue;
import com.bbh.itss.beadle.internal.domain.jira.JiraVersion;

import java.util.Collection;
import java.util.List;

public interface JiraPort {

    boolean connected();

    List<JiraVersion> versions(String project);

    List<JiraIssue> epics(String project, String fixVersion);

    List<JiraIssue> stories(String project, String fixVersion, Collection<String> epicKeys);
}
