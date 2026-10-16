package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;

import java.time.LocalDate;

public record SecureCodingTicketRequest(String changeNumber, String applicationName, LocalDate implementationDate,
                                        SecureCodingRequest inputs) {

    SecureCodingTicket toTicket() {
        return SecureCodingTicket.of(changeNumber, applicationName, implementationDate,
                inputs == null ? null : inputs.toInputs());
    }
}
