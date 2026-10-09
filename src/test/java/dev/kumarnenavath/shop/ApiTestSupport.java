package dev.kumarnenavath.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for the API tests. All test classes share one Spring context and one in-memory
 * database, so every test creates its own users, categories and products with unique names
 * instead of relying on the database being empty.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiTestSupport.ClockConfig.class)
public abstract class ApiTestSupport {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    protected record Account(long id, String email, String token) {}

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected TestClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected Account signup() throws Exception {
        String email = unique("user") + "@example.com";
        long id = body(mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isCreated())).get("id").asLong();
        return new Account(id, email, login(email, "correct-horse"));
    }

    protected String login(String email, String password) throws Exception {
        return body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())).get("token").asText();
    }

    /** The admin is created on startup from shop.admin.* in application.properties. */
    protected String adminToken() throws Exception {
        return login("admin@shop.local", "admin-local-only");
    }

    protected static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected long createCategory(String admin, String name) throws Exception {
        return body(mvc.perform(as(admin, post("/api/categories")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected long createProduct(String admin, long categoryId, long priceCents, int stock) throws Exception {
        return body(mvc.perform(as(admin, post("/api/products")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + unique("product") + "\",\"categoryId\":" + categoryId
                                + ",\"priceCents\":" + priceCents + ",\"stock\":" + stock + "}"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** A product in a fresh category, for tests that don't care about the catalog. */
    protected long createProduct(long priceCents, int stock) throws Exception {
        String admin = adminToken();
        return createProduct(admin, createCategory(admin, unique("category")), priceCents, stock);
    }

    protected ResultActions addToCart(String token, long productId, int quantity) throws Exception {
        return mvc.perform(as(token, post("/api/cart/items")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":" + quantity + "}"));
    }

    protected int stockOf(long productId) throws Exception {
        return body(mvc.perform(get("/api/products/{id}", productId))).get("stock").asInt();
    }
}
