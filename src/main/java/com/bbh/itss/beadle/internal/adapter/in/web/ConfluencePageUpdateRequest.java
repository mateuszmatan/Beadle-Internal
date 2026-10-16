package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate;

public record ConfluencePageUpdateRequest(String title, String body, Integer version) {

    ConfluencePageUpdate toUpdate() {
        return ConfluencePageUpdate.of(title, body, version);
    }
}
