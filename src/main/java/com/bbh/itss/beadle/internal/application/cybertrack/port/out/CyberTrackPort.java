package com.bbh.itss.beadle.internal.application.cybertrack.port.out;

import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;

import java.util.Collection;
import java.util.List;

public interface CyberTrackPort {

    boolean connected();

    CreatedTicket create(SecureCodingTicket ticket);

    CyberTrackTicket read(String key);

    /**
     * Reads several tickets in one go; a key CyberTrack does not know is left out of the answer.
     */
    List<CyberTrackTicket> readAll(Collection<String> keys);
}
