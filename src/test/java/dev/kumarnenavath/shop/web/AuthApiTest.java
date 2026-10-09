package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthApiTest extends ApiTestSupport {

    @Test
    void signupCreatesCustomerWhoseTokenOpensProtectedEndpoints() throws Exception {
        String email = unique("Kumar") + "@Example.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-01T11:00:00Z"));

        mvc.perform(as(login(email, "correct-horse"), get("/api/cart")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
        Account account = signup();
        for (String body : new String[] {
                "{\"email\":\"" + account.email() + "\",\"password\":\"wrong-password\"}",
                "{\"email\":\"nobody-" + account.email() + "\",\"password\":\"correct-horse\"}"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        Account account = signup();
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email().toUpperCase() + "\",\"password\":\"another-pass\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void invalidSignupReturnsProblemDetailNamingEachField() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("email must be a well-formed email address")))
                .andExpect(jsonPath("$.detail", containsString("password size must be between 8 and 72")));
    }

    @Test
    void tokenStopsWorkingAfterItExpires() throws Exception {
        String token = signup().token();
        mvc.perform(as(token, get("/api/orders"))).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(61));

        mvc.perform(as(token, get("/api/orders")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Token expired"));
    }

    @Test
    void protectedEndpointsNeedATokenAndAdminEndpointsNeedTheAdminRole() throws Exception {
        mvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Missing bearer token"));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid token"));

        String customer = signup().token();
        mvc.perform(as(customer, post("/api/categories")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + unique("sneaky") + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Admin role required"));
    }
}
