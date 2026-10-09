package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.domain.Order;
import dev.kumarnenavath.shop.domain.OrderLine;
import dev.kumarnenavath.shop.security.AuthUser;
import dev.kumarnenavath.shop.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    public record OrderLineView(long productId, String productName, long unitPriceCents, int quantity, long lineTotalCents) {
        static OrderLineView of(OrderLine l) {
            return new OrderLineView(l.getProductId(), l.getProductName(), l.getUnitPriceCents(), l.getQuantity(),
                    l.lineTotalCents());
        }
    }

    public record OrderView(long id, String status, long totalCents, Instant createdAt, List<OrderLineView> lines) {
        static OrderView of(Order o) {
            return new OrderView(o.getId(), o.getStatus().name(), o.getTotalCents(), o.getCreatedAt(),
                    o.getLines().stream().map(OrderLineView::of).toList());
        }
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView checkout(AuthUser user) {
        return OrderView.of(orders.checkout(user.id()));
    }

    @GetMapping
    public List<OrderView> list(AuthUser user) {
        return orders.ordersFor(user.id()).stream().map(OrderView::of).toList();
    }

    @GetMapping("/{id}")
    public OrderView get(AuthUser user, @PathVariable long id) {
        return OrderView.of(orders.order(user.id(), id));
    }
}
