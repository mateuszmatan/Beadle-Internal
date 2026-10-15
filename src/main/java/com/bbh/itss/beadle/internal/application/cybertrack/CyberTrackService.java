package com.bbh.itss.beadle.internal.application.cybertrack;

import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.WithoutTransaction;
import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackUseCase;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;
import lombok.RequiredArgsConstructor;

import static com.bbh.itss.beadle.internal.domain.shared.Failures.notFound;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@UseCase
@RequiredArgsConstructor
public class CyberTrackService implements CyberTrackUseCase {

    public static final String TICKET = "CyberTrack ticket";

    private final CyberTrackPort cyberTrack;

    @Override
    @WithoutTransaction
    public CreatedTicket create(SecureCodingTicket ticket) {
        return cyberTrack.create(ticket);
    }

    @Override
    @WithoutTransaction
    public CyberTrackTicket read(String key) {
        String trimmed = trimToNull(key);
        if (trimmed == null) {
            throw notFound(TICKET, key);
        }
        return cyberTrack.read(trimmed);
    }
}
