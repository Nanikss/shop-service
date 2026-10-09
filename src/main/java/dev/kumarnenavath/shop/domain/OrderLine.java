package dev.kumarnenavath.shop.domain;

import jakarta.persistence.*;

/** Snapshot of a product at checkout time: name and unit price are copied, not referenced. */
@Entity
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    private long unitPriceCents;

    private int quantity;

    protected OrderLine() {}

    OrderLine(Order order, long productId, String productName, long unitPriceCents, int quantity) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
    }

    public long lineTotalCents() { return Math.multiplyExact(unitPriceCents, quantity); }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public int getQuantity() { return quantity; }
}
