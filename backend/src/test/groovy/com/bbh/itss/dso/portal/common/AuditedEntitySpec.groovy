package com.bbh.itss.dso.portal.common

import spock.lang.Specification

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
}
