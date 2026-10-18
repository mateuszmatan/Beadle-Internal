package com.bbh.itss.beadle.internal.domain.confluence;

import com.bbh.itss.beadle.internal.domain.shared.CallTarget;
import com.bbh.itss.beadle.internal.domain.shared.ValidationProblems;

import static com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage.TITLE_MAX;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ConfluencePageUpdate(String title, String body, Integer version) implements CallTarget {

    public static final String VERSION_MESSAGE = "must be the version being replaced, 1 or more";

    public ConfluencePageUpdate {
        title = trimToNull(title);
    }

    public static ConfluencePageUpdate of(String title, String body, Integer version) {
        ConfluencePageUpdate update = new ConfluencePageUpdate(title, body, version);
        ValidationProblems problems = new ValidationProblems()
                .require("title", update.title()).fits("title", update.title(), TITLE_MAX)
                .require("body", trimToNull(update.body()))
                .require("version", update.version());
        if (update.version() != null && update.version() < 1) {
            problems.add("version", VERSION_MESSAGE);
        }
        problems.throwIfAny();
        return update;
    }

    public boolean replaces(ConfluencePage page) {
        return version != null && version == page.version();
    }

    @Override
    public String callTarget() {
        return title;
    }
}
