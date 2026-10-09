package dev.kumarnenavath.shop.domain;

import dev.kumarnenavath.shop.error.ConflictException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An order created at checkout. Line prices are copied from the product at that moment, so later
 * catalog price changes never alter what the customer was charged. Only PENDING_PAYMENT can move,
 * and {@code @Version} stops a "succeeded" and a "failed" event for the same order both winning.
 */
@Entity
@Table(name = "orders", indexes = @Index(name = "idx_order_user", columnList = "user_id"))
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    private long totalCents;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderLine> lines = new ArrayList<>();

    @Version
    private long version;

    protected Order() {}

    public Order(long userId, Instant createdAt) {
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public void addLine(Product product, int quantity) {
        OrderLine line = new OrderLine(this, product.getId(), product.getName(), product.getPriceCents(), quantity);
        lines.add(line);
        totalCents = Math.addExact(totalCents, line.lineTotalCents());
    }

    /** Returns false if the order is already paid (same outcome via a new event id), true if it changed. */
    public boolean markPaid() {
        if (status == OrderStatus.PAID) return false;
        requirePending("mark paid");
        status = OrderStatus.PAID;
        return true;
    }

    /** Returns false if the order is already cancelled, true if it changed (the caller then releases stock). */
    public boolean cancel() {
        if (status == OrderStatus.CANCELLED) return false;
        requirePending("cancel");
        status = OrderStatus.CANCELLED;
        return true;
    }

    private void requirePending(String action) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("Can't " + action + " order " + id + " in status " + status);
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public long getTotalCents() { return totalCents; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderLine> getLines() { return Collections.unmodifiableList(lines); }
}
