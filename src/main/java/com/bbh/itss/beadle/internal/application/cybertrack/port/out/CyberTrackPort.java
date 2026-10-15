package com.bbh.itss.beadle.internal.application.cybertrack.port.out;

import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;

public interface CyberTrackPort {

    boolean connected();

    CreatedTicket create(SecureCodingTicket ticket);

    CyberTrackTicket read(String key);
}
