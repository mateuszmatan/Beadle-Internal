package com.bbh.itss.beadle.internal.application.cybertrack.port.out;

import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket;

import java.util.List;
import java.util.Optional;

public interface TrackedTicketPort {

    /**
     * Starts following a ticket that was just created. The ticket exists in CyberTrack whatever happens here, so a
     * ticket that cannot be written down is reported in the log and never fails its creation.
     */
    void track(TrackedTicket ticket);

    void save(TrackedTicket ticket);

    /**
     * The tickets not closed yet, oldest first.
     */
    List<TrackedTicket> open();

    /**
     * The newest ticket not closed yet that was raised for a change. A lookup that fails finds nothing, so that the
     * ticket is raised rather than the request failed.
     */
    Optional<TrackedTicket> openFor(String changeNumber);
}
