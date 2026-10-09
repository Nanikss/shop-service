package dev.kumarnenavath.shop.domain;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * A payment webhook event that has been applied. The provider's event id is the primary key, so a
 * second insert of the same id fails in the database even when two deliveries race each other.
 */
@Entity
public class ProcessedEvent implements Persistable<String> {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Instant processedAt;

    @Transient
    private boolean isNew = true;

    protected ProcessedEvent() {}

    public ProcessedEvent(String eventId, String type, Instant processedAt) {
        this.eventId = eventId;
        this.type = type;
        this.processedAt = processedAt;
    }

    /** Always persist, never merge: a duplicate id must fail loudly rather than be silently updated. */
    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    void markLoaded() { isNew = false; }

    @Override
    public String getId() { return eventId; }
    public String getType() { return type; }
    public Instant getProcessedAt() { return processedAt; }
}
