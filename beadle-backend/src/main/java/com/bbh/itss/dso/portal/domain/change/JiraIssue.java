package com.bbh.itss.dso.portal.domain.change;

import java.time.LocalDate;

public record JiraIssue(String key, String summary, String status, String epicKey, LocalDate updated) {

    public String line() {
        return key + " " + summary + (status == null ? "" : " (" + status + ")");
    }
}
