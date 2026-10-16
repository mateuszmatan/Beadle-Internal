package com.bbh.itss.beadle.internal.domain.confluence;

import com.bbh.itss.beadle.internal.domain.shared.CallTarget;
import com.bbh.itss.beadle.internal.domain.shared.ValidationProblems;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public record NewConfluencePage(String spaceKey, String title, String parentId, String body) implements CallTarget {

    public static final int TITLE_MAX = 255;
    public static final String REQUIRED = "is required";

    public NewConfluencePage {
        spaceKey = trimToNull(spaceKey);
        title = trimToNull(title);
        parentId = trimToNull(parentId);
    }

    public static NewConfluencePage of(String spaceKey, String title, String parentId, String body) {
        NewConfluencePage page = new NewConfluencePage(spaceKey, title, parentId, body);
        ValidationProblems problems = new ValidationProblems()
                .require("spaceKey", page.spaceKey(), REQUIRED)
                .require("title", page.title(), REQUIRED).fits("title", page.title(), TITLE_MAX)
                .require("body", trimToNull(page.body()), REQUIRED);
        problems.throwIfAny();
        return page;
    }

    @Override
    public String callTarget() {
        return spaceKey + "/" + title;
    }
}
