package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CheckoutApiTest extends ApiTestSupport {

    @Test
    void checkoutSnapshotsPricesReservesStockAndEmptiesCart() throws Exception {
        Account buyer = signup();
        long pen = createProduct(1_999, 10);
        long pad = createProduct(550, 4);
        addToCart(buyer.token(), pen, 2);
        addToCart(buyer.token(), pad, 3);

        long orderId = body(mvc.perform(as(buyer.token(), post("/api/orders/checkout")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalCents").value(2 * 1_999 + 3 * 550))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0].unitPriceCents").value(1_999))
                .andExpect(jsonPath("$.lines[0].lineTotalCents").value(3_998))
                .andExpect(jsonPath("$.lines[1].lineTotalCents").value(1_650))
                .andExpect(jsonPath("$.createdAt").value("2026-10-01T10:00:00Z")))
                .get("id").asLong();

        assertThat(stockOf(pen)).isEqualTo(8);
        assertThat(stockOf(pad)).isEqualTo(1);
        mvc.perform(as(buyer.token(), get("/api/cart"))).andExpect(jsonPath("$.items", empty()));
        mvc.perform(as(buyer.token(), get("/api/orders")))
                .andExpect(jsonPath("$[*].id", hasItem((int) orderId)));

        // Orders are private: someone else gets a 404, not a 403 that confirms the id exists.
        mvc.perform(as(signup().token(), get("/api/orders/{id}", orderId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void stockCheckedAtCheckoutStopsASecondBuyerOverselling() throws Exception {
        long product = createProduct(5_000, 3);
        String alice = signup().token();
        String bob = signup().token();
        addToCart(alice, product, 2).andExpect(status().isOk());
        addToCart(bob, product, 2).andExpect(status().isOk()); // fine at add time: carts don't reserve

        mvc.perform(as(alice, post("/api/orders/checkout"))).andExpect(status().isCreated());
        mvc.perform(as(bob, post("/api/orders/checkout")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only 1 of product " + product + " in stock, requested 2"));

        assertThat(stockOf(product)).isEqualTo(1);
        mvc.perform(as(bob, get("/api/cart"))).andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(as(bob, get("/api/orders"))).andExpect(jsonPath("$", empty()));
    }

    @Test
    void emptyCartCannotBeCheckedOut() throws Exception {
        mvc.perform(as(signup().token(), post("/api/orders/checkout")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cart is empty"));
    }
}
