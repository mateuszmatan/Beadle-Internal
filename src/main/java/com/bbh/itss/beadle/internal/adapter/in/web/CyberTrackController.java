package com.bbh.itss.beadle.internal.adapter.in.web;

import com.bbh.itss.beadle.internal.application.cybertrack.port.in.CyberTrackUseCase;
import com.bbh.itss.beadle.internal.domain.cybertrack.CreatedTicket;
import com.bbh.itss.beadle.internal.domain.cybertrack.CyberTrackTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

@RestController
@RequestMapping("/api/internal/cybertrack/tickets")
@RequiredArgsConstructor
public class CyberTrackController {

    private final CyberTrackUseCase cyberTrack;

    @PostMapping
    public ResponseEntity<CreatedTicket> create(@RequestBody SecureCodingTicketRequest request) {
        CreatedTicket created = cyberTrack.create(request.toTicket());
        return created(fromCurrentRequest().path("/{key}").buildAndExpand(created.key()).toUri()).body(created);
    }

    @GetMapping("/{key}")
    public CyberTrackTicket read(@PathVariable String key) {
        return cyberTrack.read(key);
    }
}
