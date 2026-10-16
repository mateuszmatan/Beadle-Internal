package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.application.status.port.in.StatusUseCase;
import com.bbh.itss.beadle.internal.domain.status.ServiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class StatusController {

    private final StatusUseCase status;

    @GetMapping("/status")
    public ServiceStatus status() {
        return status.status();
    }
}
