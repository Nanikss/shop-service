package dev.kumarnenavath.shop.service;

import dev.kumarnenavath.shop.ApiTestSupport;
import dev.kumarnenavath.shop.repository.CartItemRepository;
import dev.kumarnenavath.shop.repository.OrderRepository;
import dev.kumarnenavath.shop.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two buyers race for the last unit. Instead of relying on thread timing, the interleaving is
 * forced: Alice's transaction reads the product, Bob checks out and commits in his own
 * transaction, then Alice's checkout decrements from her now-stale read.
 */
class CheckoutConcurrencyTest extends ApiTestSupport {

    @Autowired OrderService orderService;
    @Autowired CartService cartService;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired CartItemRepository cartItems;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void checkoutFromAStaleStockReadFailsInsteadOfOverselling() throws Exception {
        long productId = createProduct(2_500, 1);
        long alice = signup().id();
        long bob = signup().id();
        cartService.add(alice, productId, 1);
        cartService.add(bob, productId, 1);

        TransactionTemplate aliceTx = new TransactionTemplate(txManager);
        TransactionTemplate bobTx = new TransactionTemplate(txManager);
        bobTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> aliceTx.executeWithoutResult(status -> {
            assertThat(products.findById(productId).orElseThrow().getStock()).isEqualTo(1); // Alice sees 1 left
            bobTx.executeWithoutResult(s -> orderService.checkout(bob));                     // Bob buys it and commits
            orderService.checkout(alice);                                                    // Alice still sees 1 left
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(products.findById(productId).orElseThrow().getStock()).isZero();
        assertThat(orders.findByUserIdOrderByIdDesc(bob)).hasSize(1);
        assertThat(orders.findByUserIdOrderByIdDesc(alice)).isEmpty();
        // Alice's whole checkout rolled back, so her cart is intact for a retry.
        assertThat(cartItems.findByUserIdOrderByIdAsc(alice)).hasSize(1);
    }
}
