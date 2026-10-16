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

import java.util.List;

import static com.bbh.itss.beadle.internal.domain.confluence.NewConfluencePage.REQUIRED;
import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@UseCase
@RequiredArgsConstructor
public class ConfluenceService implements ConfluenceUseCase {

    public static final String PAGE = "Confluence page";

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
        return confluence.create(page);
    }

    @Override
    @WithoutTransaction
    public ConfluencePage update(String id, ConfluencePageUpdate update) {
        return confluence.update(required(id), update);
    }

    private static String required(String id) {
        String trimmed = trimToNull(id);
        if (trimmed == null) {
            throw notFound(PAGE, id);
        }
        return trimmed;
    }
}
