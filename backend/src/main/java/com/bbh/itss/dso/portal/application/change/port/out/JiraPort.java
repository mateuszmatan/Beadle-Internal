package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.JiraVersion;

import java.util.Collection;
import java.util.List;

public interface JiraPort {

    boolean connected();

    List<JiraVersion> versions(String project);

    List<JiraIssue> epics(String project, String fixVersion);

    List<JiraIssue> stories(String project, String fixVersion, Collection<String> epicKeys);

    List<JiraIssue> issues(String project, Collection<String> keys);
}
