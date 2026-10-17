package com.bbh.itss.beadle.internal.domain.shared;

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PRIVATE;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.Strings.CS;

@NoArgsConstructor(access = PRIVATE)
public final class Text {

    public static boolean isUrl(String value) {
        return CS.startsWithAny(value, "http://", "https://");
    }

    public static int bytes(String value) {
        return value.getBytes(UTF_8).length;
    }

    /**
     * The text cut to {@code maxBytes} in UTF-8 with an ellipsis, without splitting a character.
     */
    public static String abbreviateBytes(String text, int maxBytes) {
        if (text == null || bytes(text) <= maxBytes) {
            return text;
        }
        int[] used = {0};
        return text.codePoints().takeWhile(point -> (used[0] += Character.toString(point).getBytes(UTF_8).length)
                        <= maxBytes - 3)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().stripTrailing() + "...";
    }

    /**
     * The values trimmed, without blanks and duplicates, in their order.
     */
    public static List<String> clean(List<String> values) {
        return emptyIfNull(values).stream().filter(StringUtils::isNotBlank).map(String::trim).distinct().toList();
    }
}
