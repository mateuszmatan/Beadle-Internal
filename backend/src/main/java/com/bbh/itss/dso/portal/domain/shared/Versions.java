package com.bbh.itss.dso.portal.domain.shared;

public final class Versions {

    private Versions() {
    }

    public static void requireCurrent(Long expected, long current) {
        if (expected != null && expected != current) {
            throw ConflictException.staleVersion();
        }
    }
}
