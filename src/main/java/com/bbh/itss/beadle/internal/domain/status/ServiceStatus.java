package com.bbh.itss.beadle.internal.domain.status;

public record ServiceStatus(SystemStatus jira, SystemStatus cyberTrack, SystemStatus confluence,
                            SystemStatus qtest) {
}
