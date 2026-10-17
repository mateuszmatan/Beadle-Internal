package com.bbh.itss.beadle.internal.application.confluence;

import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.WithoutTransaction;
import com.bbh.itss.beadle.internal.application.confluence.port.in.ConfluenceUseCase;
import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePage;
import com.bbh.itss.beadle.internal.domain.confluence.ConfluencePageUpdate;
import com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage;
import com.bbh.itss.beadle.internal.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage.REQUIRED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class ConfluenceService implements ConfluenceUseCase {


    private final ConfluencePort confluence;

    @Override
    @WithoutTransaction
    public List<ConfluencePage> find(String spaceKey, String title) {
        String space = trimToNull(spaceKey);
        String name = trimToNull(title);
        new ValidationProblems().require("spaceKey", space, REQUIRED).require("title", name, REQUIRED).throwIfAny();
        return confluence.find(space, name).stream().map(ConfluencePage::withoutBody).toList();
    }

    @Override
    @WithoutTransaction
    public ConfluencePage get(String id) {
        return confluence.get(required(id));
    }

    @Override
    @WithoutTransaction
    public ConfluencePage create(NewConfluencePage page) {
        ConfluencePage created = confluence.create(page);
        log.info("Created the Confluence page {} '{}' in space {}", created.id(), created.title(), created.spaceKey());
        return created;
    }

    @Override
    @WithoutTransaction
    public ConfluencePage update(String id, ConfluencePageUpdate update) {
        ConfluencePage updated = confluence.update(required(id), update);
        log.info("Updated the Confluence page {} to version {}", updated.id(), updated.version());
        return updated;
    }

    private static String required(String id) {
        String trimmed = trimToNull(id);
        if (trimmed == null) {
            throw notFound(ConfluencePage.NAME, id);
        }
        return trimmed;
    }
}
