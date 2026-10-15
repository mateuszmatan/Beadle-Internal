package com.bbh.itss.beadle.internal.domain.cybertrack;

import com.bbh.itss.beadle.internal.domain.shared.ValidationProblems;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.bbh.itss.beadle.internal.domain.cybertrack.SecureCoding.REQUIRED;
import static com.bbh.itss.beadle.internal.domain.shared.Text.abbreviateBytes;
import static java.time.format.DateTimeFormatter.ofPattern;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record SecureCodingTicket(String changeNumber, String applicationName, LocalDate implementationDate,
                                 SecureCoding inputs) {

    public static final int SUMMARY_MAX = 255;
    public static final int CHANGE_NUMBER_MAX = 40;
    public static final int APPLICATION_MAX = 255;

    private static final DateTimeFormatter TICKET_DATE = ofPattern("MMdduuuu");

    public SecureCodingTicket {
        changeNumber = trimToNull(changeNumber);
        applicationName = trimToNull(applicationName);
    }

    public static SecureCodingTicket of(String changeNumber, String applicationName, LocalDate implementationDate,
                                        SecureCoding inputs) {
        ValidationProblems problems = new ValidationProblems();
        SecureCodingTicket ticket = new SecureCodingTicket(changeNumber, applicationName, implementationDate,
                getIfNull(inputs, SecureCoding.NONE));
        problems.require("changeNumber", ticket.changeNumber(), REQUIRED)
                .fits("changeNumber", ticket.changeNumber(), CHANGE_NUMBER_MAX)
                .require("applicationName", ticket.applicationName(), REQUIRED)
                .fits("applicationName", ticket.applicationName(), APPLICATION_MAX)
                .require("implementationDate", implementationDate, REQUIRED);
        ticket.inputs().require(problems.at("inputs"));
        problems.throwIfAny();
        return ticket;
    }

    public String summary() {
        return abbreviateBytes(inputs.apoNumber() + "_" + applicationName + "-" + ticketDate(), SUMMARY_MAX);
    }

    public String description() {
        return String.join("\n",
                "APO number: " + inputs.apoNumber(),
                "Application: " + applicationName,
                "Implementation date: " + ticketDate(),
                "Bitbucket URL (SAST scan): " + inputs.bitbucketUrl(),
                "Artifact link (OSA - Nexus IQ scan): " + inputs.artifactLink(),
                "QC application link (DAST scan): " + inputs.qcApplicationLink(),
                "ProTech change: " + changeNumber);
    }

    private String ticketDate() {
        return implementationDate == null ? null : TICKET_DATE.format(implementationDate);
    }
}
