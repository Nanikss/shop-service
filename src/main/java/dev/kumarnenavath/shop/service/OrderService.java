package dev.kumarnenavath.shop.service;

import dev.kumarnenavath.shop.domain.CartItem;
import dev.kumarnenavath.shop.domain.Order;
import dev.kumarnenavath.shop.error.NotFoundException;
import dev.kumarnenavath.shop.repository.CartItemRepository;
import dev.kumarnenavath.shop.repository.OrderRepository;
import dev.kumarnenavath.shop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class OrderService {

    private final CartItemRepository cart;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final Clock clock;

    public OrderService(CartItemRepository cart, ProductRepository products, OrderRepository orders, Clock clock) {
        this.cart = cart;
        this.products = products;
        this.orders = orders;
        this.clock = clock;
    }

    /**
     * Turns the cart into a PENDING_PAYMENT order and reserves stock, all in one transaction.
     * Each decremented product is written with {@code WHERE version = ?}; if another checkout
     * changed that product since we read it, the flush fails with
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}, everything rolls
     * back (cart included) and the client gets a 409 it can retry.
     */
    @Transactional
    public Order checkout(long userId) {
        List<CartItem> items = cart.findByUserIdOrderByIdAsc(userId);
        if (items.isEmpty()) throw new IllegalStateException("Cart is empty");

        Order order = new Order(userId, clock.instant());
        for (CartItem item : items) {
            item.getProduct().reserve(item.getQuantity());
            order.addLine(item.getProduct(), item.getQuantity());
        }
        // Flush now so a version conflict surfaces here, before the order is written.
        products.flush();
        orders.save(order);
        cart.deleteAll(items);
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> ordersFor(long userId) {
        return orders.findByUserIdOrderByIdDesc(userId);
    }

    /** Another user's order is reported as not found, so order ids can't be probed. */
    @Transactional(readOnly = true)
    public Order order(long userId, long orderId) {
        return orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
    }
}
