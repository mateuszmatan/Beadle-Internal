package com.bbh.itss.beadle.internal.application.status;

import com.bbh.itss.beadle.internal.application.UseCase;
import com.bbh.itss.beadle.internal.application.WithoutTransaction;
import com.bbh.itss.beadle.internal.application.confluence.port.out.ConfluencePort;
import com.bbh.itss.beadle.internal.application.cybertrack.port.out.CyberTrackPort;
import com.bbh.itss.beadle.internal.application.jira.port.out.JiraPort;
import com.bbh.itss.beadle.internal.application.qtest.port.out.QTestPort;
import com.bbh.itss.beadle.internal.application.status.port.in.StatusUseCase;
import com.bbh.itss.beadle.internal.domain.status.ServiceStatus;
import lombok.RequiredArgsConstructor;

import static com.bbh.itss.beadle.internal.domain.status.SystemStatus.of;

@UseCase
@RequiredArgsConstructor
public class StatusService implements StatusUseCase {

    private final JiraPort jira;
    private final CyberTrackPort cyberTrack;
    private final ConfluencePort confluence;
    private final QTestPort qtest;

    @Override
    @WithoutTransaction
    public ServiceStatus status() {
        return new ServiceStatus(of(jira.connected()), of(cyberTrack.connected()), of(confluence.connected()),
                of(qtest.connected()));
    }
}
