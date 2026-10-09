package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.domain.CartItem;
import dev.kumarnenavath.shop.security.AuthUser;
import dev.kumarnenavath.shop.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    public record AddItemRequest(@NotNull Long productId, @Positive int quantity) {}
    public record QuantityRequest(@Positive int quantity) {}

    public record CartLineView(long productId, String name, long unitPriceCents, int quantity, long subtotalCents) {
        static CartLineView of(CartItem i) {
            return new CartLineView(i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getPriceCents(),
                    i.getQuantity(), i.subtotalCents());
        }
    }

    public record CartView(List<CartLineView> items, long totalCents) {
        static CartView of(List<CartItem> items) {
            List<CartLineView> lines = items.stream().map(CartLineView::of).toList();
            return new CartView(lines, lines.stream().mapToLong(CartLineView::subtotalCents).sum());
        }
    }

    @GetMapping
    public CartView view(AuthUser user) {
        return CartView.of(cart.items(user.id()));
    }

    @PostMapping("/items")
    public CartView add(AuthUser user, @Valid @RequestBody AddItemRequest req) {
        cart.add(user.id(), req.productId(), req.quantity());
        return view(user);
    }

    @PutMapping("/items/{productId}")
    public CartView update(AuthUser user, @PathVariable long productId, @Valid @RequestBody QuantityRequest req) {
        cart.setQuantity(user.id(), productId, req.quantity());
        return view(user);
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(AuthUser user, @PathVariable long productId) {
        cart.remove(user.id(), productId);
    }
}
