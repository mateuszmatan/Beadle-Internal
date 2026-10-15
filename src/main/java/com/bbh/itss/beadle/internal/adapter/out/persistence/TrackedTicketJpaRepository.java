package com.bbh.itss.beadle.internal.adapter.out.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

interface TrackedTicketJpaRepository extends Repository<TrackedTicketEntity, String> {

    TrackedTicketEntity save(TrackedTicketEntity entity);

    List<TrackedTicketEntity> findByClosedAtIsNullOrderByCreatedAtAscTicketKeyAsc();

    Optional<TrackedTicketEntity> findFirstByChangeNumberAndClosedAtIsNullOrderByCreatedAtDesc(String changeNumber);
}
