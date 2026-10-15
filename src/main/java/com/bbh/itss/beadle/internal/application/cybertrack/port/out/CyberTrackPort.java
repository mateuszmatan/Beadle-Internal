package com.bbh.itss.beadle.internal.application.cybertrack.port.out;

import com.bbh.itss.beadle.internal.domain.cybertrack.SecureCodingTicket;

public interface CyberTrackPort {

    boolean connected();

    String create(SecureCodingTicket ticket);
}
