package com.bbh.itss.dso.portal.domain.shared;

import java.util.List;

public final class Text {

    private Text() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public static String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    public static boolean isUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    public static String withoutTrailingSlash(String url) {
        return url.trim().replaceAll("/+$", "");
    }

    public static List<String> clean(List<String> values) {
        return trimmed(values).stream().distinct().toList();
    }

    public static List<String> trimmed(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> !isBlank(value)).map(String::trim).toList();
    }
}
