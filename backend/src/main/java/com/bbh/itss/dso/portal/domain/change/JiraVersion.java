package com.bbh.itss.dso.portal.domain.change;

import java.time.LocalDate;
import java.util.Comparator;

import static java.util.Comparator.comparing;
import static java.util.Comparator.nullsFirst;
import static java.util.Comparator.reverseOrder;

public record JiraVersion(String name, boolean released, LocalDate releaseDate) {

    public static final Comparator<JiraVersion> UNRELEASED_NEWEST_FIRST = comparing(JiraVersion::released)
            .thenComparing(JiraVersion::releaseDate, nullsFirst(reverseOrder()))
            .thenComparing(JiraVersion::name, reverseOrder());
}
