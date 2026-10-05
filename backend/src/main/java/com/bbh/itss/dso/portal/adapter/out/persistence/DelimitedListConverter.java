package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.shared.Text;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

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
        return distinct ? Text.clean(values) : Text.trimmed(values);
    }

    @Converter(autoApply = true)
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
