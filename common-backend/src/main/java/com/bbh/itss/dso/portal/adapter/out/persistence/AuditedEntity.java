package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.shared.Failures;
import com.bbh.itss.dso.portal.domain.shared.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MICROS;
import static lombok.AccessLevel.PACKAGE;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

@MappedSuperclass
@Getter
public abstract class AuditedEntity {

    @Column(updatable = false)
    @Setter(PACKAGE)
    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        Instant now = Timestamps.now();
        createdAt = getIfNull(createdAt, now);
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        touch();
    }

    void touch() {
        Instant now = Timestamps.now();
        updatedAt = updatedAt == null || now.isAfter(updatedAt) ? now : updatedAt.plus(1, MICROS);
    }

    static <T> void replace(List<T> current, List<T> replacement) {
        if (!current.equals(replacement)) {
            current.clear();
            current.addAll(replacement);
        }
    }

    static <E extends AuditedEntity> E current(Optional<E> found, long expectedVersion) {
        return found.filter(entity -> entity.version() == expectedVersion)
                .orElseThrow(Failures::staleVersion);
    }
}
