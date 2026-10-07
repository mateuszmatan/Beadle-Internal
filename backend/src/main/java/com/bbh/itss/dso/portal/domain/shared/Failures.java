package com.bbh.itss.dso.portal.domain.shared;

import lombok.NoArgsConstructor;

import java.util.NoSuchElementException;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class Failures {

    public static final String STALE_VERSION =
            "The record was changed by someone else in the meantime. Reload it and apply your change again.";

    public static NoSuchElementException notFound(String entity, Object id) {
        return new NoSuchElementException(entity + " " + id + " does not exist");
    }

    public static IllegalStateException staleVersion() {
        return new IllegalStateException(STALE_VERSION);
    }
}
