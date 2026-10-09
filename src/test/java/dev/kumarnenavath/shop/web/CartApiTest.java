package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CartApiTest extends ApiTestSupport {

    @Test
    void addingTheSameProductAccumulatesAndTotalsAreInCents() throws Exception {
        String token = signup().token();
        long pen = createProduct(1_999, 10);
        long pad = createProduct(550, 10);

        addToCart(token, pen, 2).andExpect(status().isOk());
        addToCart(token, pen, 1).andExpect(status().isOk());
        addToCart(token, pad, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].subtotalCents").value(5_997))
                .andExpect(jsonPath("$.totalCents").value(5_997 + 1_650));
    }

    @Test
    void quantitiesAboveStockAreRejected() throws Exception {
        String token = signup().token();
        long product = createProduct(1_000, 5);

        addToCart(token, product, 6)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only 5 of product " + product + " in stock, requested 6"));
        addToCart(token, product, 3).andExpect(status().isOk());
        addToCart(token, product, 3).andExpect(status().isConflict()); // 3 + 3 > 5

        mvc.perform(as(token, put("/api/cart/items/{id}", product)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":6}"))
                .andExpect(status().isConflict());
        mvc.perform(as(token, put("/api/cart/items/{id}", product)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    @Test
    void invalidQuantitiesAndRemoval() throws Exception {
        String token = signup().token();
        long product = createProduct(1_000, 5);

        addToCart(token, product, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("quantity must be greater than 0")));
        addToCart(token, 999_999, 1).andExpect(status().isNotFound());

        addToCart(token, product, 2).andExpect(status().isOk());
        mvc.perform(as(token, delete("/api/cart/items/{id}", product))).andExpect(status().isNoContent());
        mvc.perform(as(token, delete("/api/cart/items/{id}", product))).andExpect(status().isNoContent());
        mvc.perform(as(token, get("/api/cart")))
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalCents").value(0));
    }
}
