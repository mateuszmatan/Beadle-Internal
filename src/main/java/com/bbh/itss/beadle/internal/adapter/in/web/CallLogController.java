package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.application.calllog.port.in.CallLogUseCase;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogPage;
import com.bbh.itss.beadle.internal.domain.calllog.CallLogQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/internal/call-log")
@RequiredArgsConstructor
public class CallLogController {

    private final CallLogUseCase callLog;

    @GetMapping
    public CallLogPage find(@RequestParam(required = false) String correlationId,
                            @RequestParam(required = false) Instant from,
                            @RequestParam(required = false) Instant to,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        return callLog.find(CallLogQuery.of(correlationId, from, to, page, size));
    }
}
