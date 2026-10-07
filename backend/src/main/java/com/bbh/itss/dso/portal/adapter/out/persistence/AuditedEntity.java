package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.shared.Failures;
import com.bbh.itss.dso.portal.domain.shared.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@MappedSuperclass
public abstract class AuditedEntity {

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        Instant now = Timestamps.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        touch();
    }

    void touch() {
        Instant now = Timestamps.now();
        updatedAt = updatedAt == null || now.isAfter(updatedAt) ? now : updatedAt.plus(1, ChronoUnit.MICROS);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    static <E extends AuditedEntity> E current(Optional<E> found, long expectedVersion) {
        return found.filter(entity -> entity.getVersion() == expectedVersion)
                .orElseThrow(Failures::staleVersion);
    }
}
