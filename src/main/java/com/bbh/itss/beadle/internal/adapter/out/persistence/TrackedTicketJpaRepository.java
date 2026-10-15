package com.bbh.itss.beadle.internal.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface TrackedTicketJpaRepository extends Repository<TrackedTicketEntity, String> {

    TrackedTicketEntity save(TrackedTicketEntity entity);

    List<TrackedTicketEntity> findByClosedAtIsNullOrderByCreatedAtAscTicketKeyAsc();

    Optional<TrackedTicketEntity> findFirstByChangeNumberAndClosedAtIsNullOrderByCreatedAtDesc(String changeNumber);

    Optional<TrackedTicketEntity> findById(String key);

    @Query("""
            SELECT t.ticketKey FROM TrackedTicketEntity t WHERE t.ticketKey LIKE :pattern ESCAPE '\\'
            ORDER BY LENGTH(t.ticketKey) DESC, t.ticketKey DESC""")
    List<String> findKeysLike(@Param("pattern") String pattern, Pageable page);
}
