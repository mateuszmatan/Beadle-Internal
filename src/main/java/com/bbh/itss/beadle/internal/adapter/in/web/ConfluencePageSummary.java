package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage;

public record ConfluencePageSummary(String id, String spaceKey, String title, int version, String url) {

    static ConfluencePageSummary of(ConfluencePage page) {
        return new ConfluencePageSummary(page.id(), page.spaceKey(), page.title(), page.version(), page.url());
    }
}
