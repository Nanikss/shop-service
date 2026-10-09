package dev.kumarnenavath.shop.domain;

import dev.kumarnenavath.shop.error.ConflictException;
import jakarta.persistence.*;

/**
 * A sellable item. Stock is guarded by optimistic locking: every reservation or release bumps
 * {@code version}, so two transactions that read the same stock level can't both commit a decrement.
 */
@Entity
@Table(indexes = @Index(name = "idx_product_category", columnList = "category_id"))
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private long priceCents;

    private int stock;

    @Version
    private long version;

    protected Product() {}

    public Product(String name, Category category, long priceCents, int stock) {
        if (priceCents <= 0) throw new IllegalArgumentException("Price must be positive");
        if (stock < 0) throw new IllegalArgumentException("Stock can't be negative");
        this.name = name;
        this.category = category;
        this.priceCents = priceCents;
        this.stock = stock;
    }

    /** Throws if {@code quantity} units aren't available right now. Doesn't change stock. */
    public void ensureAvailable(int quantity) {
        if (quantity > stock) {
            throw new ConflictException("Only " + stock + " of product " + id + " in stock, requested " + quantity);
        }
    }

    public void reserve(int quantity) {
        ensureAvailable(quantity);
        stock -= quantity;
    }

    public void release(int quantity) {
        stock += quantity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Category getCategory() { return category; }
    public long getPriceCents() { return priceCents; }
    public int getStock() { return stock; }
    public long getVersion() { return version; }
}
