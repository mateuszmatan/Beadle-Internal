package com.bbh.itss.beadle.internal.domain.jira;

import java.time.LocalDate;

public record JiraIssue(String key, String summary, String status, String epicKey, LocalDate updated) {
}
