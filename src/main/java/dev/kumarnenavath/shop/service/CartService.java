package dev.kumarnenavath.shop.service;

import dev.kumarnenavath.shop.domain.CartItem;
import dev.kumarnenavath.shop.domain.Product;
import dev.kumarnenavath.shop.error.NotFoundException;
import dev.kumarnenavath.shop.repository.CartItemRepository;
import dev.kumarnenavath.shop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cart quantities are checked against current stock, but nothing is reserved until checkout:
 * holding stock for abandoned carts would let a few idle sessions block real buyers.
 */
@Service
public class CartService {

    private final CartItemRepository cart;
    private final ProductRepository products;

    public CartService(CartItemRepository cart, ProductRepository products) {
        this.cart = cart;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CartItem> items(long userId) {
        return cart.findByUserIdOrderByIdAsc(userId);
    }

    /** Adds to the quantity already in the cart (or creates the line). */
    @Transactional
    public void add(long userId, long productId, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));
        cart.findByUserIdAndProductId(userId, productId).ifPresentOrElse(
                item -> {
                    int total = Math.addExact(item.getQuantity(), quantity);
                    product.ensureAvailable(total);
                    item.setQuantity(total);
                },
                () -> {
                    product.ensureAvailable(quantity);
                    cart.save(new CartItem(userId, product, quantity));
                });
    }

    /** Replaces the quantity of a line already in the cart. */
    @Transactional
    public void setQuantity(long userId, long productId, int quantity) {
        CartItem item = cart.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " is not in your cart"));
        item.getProduct().ensureAvailable(quantity);
        item.setQuantity(quantity);
    }

    /** Idempotent: removing something that isn't in the cart is not an error. */
    @Transactional
    public void remove(long userId, long productId) {
        cart.findByUserIdAndProductId(userId, productId).ifPresent(cart::delete);
    }
}
