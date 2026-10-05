package com.bbh.itss.dso.portal.adapter.out.persistence

import spock.lang.Specification

import java.time.Instant
import java.time.temporal.ChronoUnit

class AuditedEntitySpec extends Specification {

    def entity = new AuditedEntity() {}

    def "creating sets both timestamps to the same instant"() {
        when:
        entity.onCreate()

        then:
        entity.createdAt != null
        entity.createdAt.nano % 1000 == 0
        entity.updatedAt == entity.createdAt
        entity.version == 0
    }

    def "updating moves only the modification time"() {
        given:
        entity.onCreate()
        def created = entity.createdAt

        when:
        entity.onUpdate()

        then:
        entity.createdAt == created
        !entity.updatedAt.isBefore(created)
    }

    def "touching moves the modification time forward, also within the same microsecond"() {
        given:
        entity.onCreate()
        def created = entity.createdAt
        Instant ahead = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS)
        AuditedEntity.getDeclaredField('updatedAt').with {
            accessible = true
            set(entity, ahead)
        }

        when:
        entity.touch()

        then:
        entity.updatedAt == ahead.plus(1, ChronoUnit.MICROS)
        entity.createdAt == created
    }

    def "touching an entity that was never stored sets its modification time"() {
        when:
        entity.touch()

        then:
        entity.updatedAt != null
        entity.createdAt == null
    }
}
