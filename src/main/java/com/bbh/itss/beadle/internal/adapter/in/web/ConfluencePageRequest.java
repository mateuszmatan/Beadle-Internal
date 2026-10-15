package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage;

public record ConfluencePageRequest(String spaceKey, String title, String parentId, String body) {

    NewConfluencePage toPage() {
        return NewConfluencePage.of(spaceKey, title, parentId, body);
    }
}
