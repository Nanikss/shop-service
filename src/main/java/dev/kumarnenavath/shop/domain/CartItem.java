package dev.kumarnenavath.shop.domain;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_cart_user_product", columnNames = {"user_id", "product_id"}))
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;

    protected CartItem() {}

    public CartItem(long userId, Product product, int quantity) {
        this.userId = userId;
        this.product = product;
        this.quantity = quantity;
    }

    /** At the product's current price; the order snapshots the price at checkout. */
    public long subtotalCents() { return Math.multiplyExact(product.getPriceCents(), quantity); }

    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
}
