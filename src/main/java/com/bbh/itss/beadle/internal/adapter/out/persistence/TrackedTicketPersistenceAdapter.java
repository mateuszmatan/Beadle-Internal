package com.bbh.itss.beadle.internal.adapter.out.persistence;

import com.bbh.itss.beadle.internal.application.cybertrack.port.out.TrackedTicketPort;
import com.bbh.itss.beadle.internal.domain.cybertrack.TrackedTicket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class TrackedTicketPersistenceAdapter implements TrackedTicketPort {

    private final TrackedTicketJpaRepository tickets;

    @Override
    public void track(TrackedTicket ticket) {
        try {
            save(ticket);
        } catch (RuntimeException e) {
            log.error("CyberTrack ticket {} was created but cannot be followed: Beadle-GUI will not hear of its "
                    + "status changes", ticket.key(), e);
        }
    }

    @Override
    public void save(TrackedTicket ticket) {
        tickets.save(TrackedTicketEntity.of(ticket));
    }

    @Override
    public Optional<TrackedTicket> openFor(String changeNumber) {
        try {
            return tickets.findFirstByChangeNumberAndClosedAtIsNullOrderByCreatedAtDesc(changeNumber)
                    .map(TrackedTicketEntity::toDomain);
        } catch (RuntimeException e) {
            log.warn("The open CyberTrack ticket of {} could not be looked up, so a new one is raised: {}",
                    changeNumber, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<TrackedTicket> open() {
        return tickets.findByClosedAtIsNullOrderByCreatedAtAscTicketKeyAsc().stream().map(TrackedTicketEntity::toDomain)
                .toList();
    }
}
