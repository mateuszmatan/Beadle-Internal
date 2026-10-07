package com.bbh.itss.dso.portal.domain.shared;

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.startsWithAny;

@NoArgsConstructor(access = PRIVATE)
public final class Text {

    public static boolean isUrl(String value) {
        return startsWithAny(value, "http://", "https://");
    }

    public static int bytes(String value) {
        return value.getBytes(UTF_8).length;
    }

    public static String abbreviateBytes(String text, int maxBytes) {
        if (text == null || bytes(text) <= maxBytes) {
            return text;
        }
        int[] used = {0};
        return text.codePoints().takeWhile(point -> (used[0] += bytesOf(point)) <= maxBytes - 3)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().stripTrailing() + "...";
    }

    private static int bytesOf(int codePoint) {
        return codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
    }

    public static List<String> clean(List<String> values) {
        return trimmed(values).stream().distinct().toList();
    }

    public static List<String> trimmed(List<String> values) {
        return emptyIfNull(values).stream().filter(StringUtils::isNotBlank).map(String::trim).toList();
    }
}
