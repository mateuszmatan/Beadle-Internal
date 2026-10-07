package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.DateRange;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;

import java.util.Collection;
import java.util.List;

public interface JiraPort {

    boolean connected();

    List<JiraIssue> epics(String project, DateRange updated);

    List<JiraIssue> stories(String project, Collection<String> epicKeys, DateRange updated);

    List<JiraIssue> issues(String project, Collection<String> keys);
}
