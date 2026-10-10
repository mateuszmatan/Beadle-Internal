package com.bbh.itss.dso.portal.adapter.in.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.DayOfWeek;
import java.util.List;

public record SampleRequest(@NotNull DayOfWeek day,
                            @NotEmpty(message = "add at least one label")
                            List<@Pattern(regexp = "[^,]+", message = "must not contain commas") String> labels) {
}
