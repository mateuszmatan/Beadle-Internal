package com.bbh.itss.beadle.internal.domain.confluence;

public record ConfluencePage(String id, String spaceKey, String title, int version, String url, String body) {

    public static final String NAME = "Confluence page";

    public ConfluencePage replacedBy(ConfluencePageUpdate update) {
        return new ConfluencePage(id, spaceKey, update.title(), version + 1, url, update.body());
    }
}
