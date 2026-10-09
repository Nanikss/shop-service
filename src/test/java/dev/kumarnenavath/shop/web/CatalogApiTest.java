package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogApiTest extends ApiTestSupport {

    @Test
    void productsArePaginatedAndFilteredByCategoryWithoutLogin() throws Exception {
        String admin = adminToken();
        String books = unique("Books");
        long booksId = createCategory(admin, books);
        long toysId = createCategory(admin, unique("Toys"));
        long first = createProduct(admin, booksId, 1_299, 10);
        long second = createProduct(admin, booksId, 2_450, 5);
        long third = createProduct(admin, booksId, 999, 0);
        long toy = createProduct(admin, toysId, 3_000, 1);

        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(books)));

        mvc.perform(get("/api/products").param("categoryId", String.valueOf(booksId)).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[*].id", contains((int) first, (int) second)))
                .andExpect(jsonPath("$.items[0].categoryName").value(books))
                .andExpect(jsonPath("$.items[0].priceCents").value(1_299));

        mvc.perform(get("/api/products").param("categoryId", String.valueOf(booksId)).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items[*].id", contains((int) third)))
                .andExpect(jsonPath("$.items[*].id", not(hasItem((int) toy))));

        mvc.perform(get("/api/products"))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(4)));
    }

    @Test
    void productValidationAndUnknownCategory() throws Exception {
        String admin = adminToken();
        mvc.perform(as(admin, post("/api/products")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"categoryId\":1,\"priceCents\":0,\"stock\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", allOf(containsString("name"), containsString("priceCents"),
                        containsString("stock"))));

        mvc.perform(as(admin, post("/api/products")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lamp\",\"categoryId\":999999,\"priceCents\":100,\"stock\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Category 999999 not found"));
    }

    @Test
    void duplicateCategoryNameIsConflict() throws Exception {
        String admin = adminToken();
        String name = unique("Garden");
        createCategory(admin, name);
        mvc.perform(as(admin, post("/api/categories")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name.toUpperCase() + "\"}"))
                .andExpect(status().isConflict());
    }
}
