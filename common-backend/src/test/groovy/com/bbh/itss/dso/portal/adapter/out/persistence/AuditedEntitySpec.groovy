package com.bbh.itss.dso.portal.adapter.out.persistence

import spock.lang.Specification

import java.time.Instant

import static java.time.temporal.ChronoUnit.MICROS

class AuditedEntitySpec extends Specification {

    def entity = new AuditedEntity() {}

    def "creating sets both timestamps to the same microsecond and updating moves only the modification time"() {
        when:
        entity.onCreate()
        def created = entity.createdAt()

        then:
        created.nano % 1000 == 0
        entity.updatedAt() == created
        entity.version() == 0

        when:
        entity.onUpdate()

        then:
        entity.createdAt() == created
        !entity.updatedAt().isBefore(created)
    }

    def "touching moves the modification time forward, also within the same microsecond or before the first save"() {
        given:
        def fresh = new AuditedEntity() {}
        entity.onCreate()
        def created = entity.createdAt()
        Instant ahead = Instant.now().plusSeconds(60).truncatedTo(MICROS)
        AuditedEntity.getDeclaredField('updatedAt').with {
            accessible = true
            set(entity, ahead)
        }

        when:
        entity.touch()
        fresh.touch()

        then:
        entity.updatedAt() == ahead.plus(1, MICROS)
        entity.createdAt() == created
        fresh.updatedAt() != null
        fresh.createdAt() == null
    }

    def "creating keeps a creation time that was set before, as for a change raised in the past"() {
        given:
        def raised = Instant.parse('2026-10-05T09:00:00Z')
        entity.createdAt(raised)

        when:
        entity.onCreate()

        then:
        entity.createdAt() == raised
        entity.updatedAt().isAfter(raised)
    }
}
