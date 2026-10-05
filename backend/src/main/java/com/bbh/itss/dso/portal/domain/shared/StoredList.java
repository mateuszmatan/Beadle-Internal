package com.bbh.itss.dso.portal.domain.shared;

import java.nio.charset.StandardCharsets;
import java.util.List;

public record StoredList(String separator, int maxBytes) {

    public static final StoredList LINES_1000 = new StoredList("\n", 1000);
    public static final StoredList LINES_2000 = new StoredList("\n", 2000);
    public static final StoredList LINES_4000 = new StoredList("\n", 4000);
    public static final StoredList COMMAS_1000 = new StoredList(",", 1000);
    public static final StoredList COMMAS_2000 = new StoredList(",", 2000);

    public boolean fits(List<String> values) {
        return String.join(separator, values).getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }

    public void check(ValidationProblems problems, String field, List<String> values) {
        if (!fits(values)) {
            problems.add(field, "is too long: all entries together may take at most " + maxBytes + " bytes");
        }
    }
}
