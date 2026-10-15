package com.bbh.itss.beadle.internal.application.cybertrack.port.in;

import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;

public interface CyberTrackUseCase {

    CreatedTicket create(SecureCodingTicket ticket);

    CyberTrackTicket read(String key);
}
