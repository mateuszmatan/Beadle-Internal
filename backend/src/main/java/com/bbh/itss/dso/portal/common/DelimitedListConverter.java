package com.bbh.itss.dso.portal.common;

import jakarta.persistence.AttributeConverter;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public abstract class DelimitedListConverter implements AttributeConverter<List<String>, String> {

    private final String delimiter;
    private final Pattern splitter;
    private final boolean distinct;

    protected DelimitedListConverter(String delimiter) {
        this(delimiter, true);
    }

    protected DelimitedListConverter(String delimiter, boolean distinct) {
        this.delimiter = delimiter;
        this.splitter = Pattern.compile(Pattern.quote(delimiter));
        this.distinct = distinct;
    }

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        List<String> cleaned = normalize(values);
        return cleaned.isEmpty() ? null : String.join(delimiter, cleaned);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        return Text.isBlank(column) ? List.of() : normalize(Arrays.asList(splitter.split(column)));
    }

    private List<String> normalize(List<String> values) {
        return distinct ? clean(values) : trimmed(values);
    }

    public static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(v -> !Text.isBlank(v)).map(String::trim).distinct().toList();
    }

    public static List<String> trimmed(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(v -> !Text.isBlank(v)).map(String::trim).toList();
    }

    public static class Lines extends DelimitedListConverter {
        public Lines() {
            super("\n");
        }
    }

    public static class Tokens extends DelimitedListConverter {
        public Tokens() {
            super("\n", false);
        }
    }

    public static class Commas extends DelimitedListConverter {
        public Commas() {
            super(",");
        }
    }
}
