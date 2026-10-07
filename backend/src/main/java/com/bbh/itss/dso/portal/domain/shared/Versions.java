package com.bbh.itss.dso.portal.domain.shared;

import lombok.NoArgsConstructor;

import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class Versions {

    public static void requireCurrent(Long expected, long current) {
        if (expected != null && expected != current) {
            throw staleVersion();
        }
    }
}
