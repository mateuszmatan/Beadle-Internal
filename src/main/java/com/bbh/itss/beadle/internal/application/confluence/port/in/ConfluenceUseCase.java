package com.bbh.itss.beadle.internal.application.confluence.port.in;

import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate;
import com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage;

import java.util.List;

public interface ConfluenceUseCase {

    List<ConfluencePage> find(String spaceKey, String title);

    ConfluencePage get(String id);

    ConfluencePage create(NewConfluencePage page);

    ConfluencePage update(String id, ConfluencePageUpdate update);
}
