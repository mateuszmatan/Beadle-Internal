package com.bbh.itss.dso.portal.application.change.port.out;

import com.bbh.itss.dso.portal.domain.change.SecureCodingTicket;

public interface CyberTrackPort {

    boolean connected();

    String create(SecureCodingTicket ticket);
}
