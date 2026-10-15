package com.bbh.itss.beadle.internal.application.cybertrack.port.in;

import com.bbh.itss.beadle.internal.domain.callback.PollReport;

public interface CyberTrackPollUseCase {

    /**
     * Reads the open tickets Beadle created and tells Beadle-GUI of every one that moved to another status. A ticket
     * whose callback failed keeps its former status, so it is reported again on the next poll.
     */
    PollReport poll();
}
