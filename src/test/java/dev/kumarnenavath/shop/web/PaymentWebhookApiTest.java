package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.ApiTestSupport;
import dev.kumarnenavath.shop.security.WebhookSignature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentWebhookApiTest extends ApiTestSupport {

    @Autowired WebhookSignature signature;

    private record PlacedOrder(String token, long orderId, long productId, long totalCents) {}

    /** A buyer orders 2 of a product that had 5 in stock, leaving 3. */
    private PlacedOrder placeOrder() throws Exception {
        String token = signup().token();
        long productId = createProduct(1_250, 5);
        addToCart(token, productId, 2);
        long orderId = body(mvc.perform(as(token, post("/api/orders/checkout")))).get("id").asLong();
        return new PlacedOrder(token, orderId, productId, 2_500);
    }

    private static String event(String id, String type, long orderId, long amountCents, Instant created) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"created\":" + created.getEpochSecond()
                + ",\"data\":{\"orderId\":" + orderId + ",\"amountCents\":" + amountCents + "}}";
    }

    private ResultActions deliver(String body, String sig) throws Exception {
        return mvc.perform(post("/api/webhooks/payments").contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", sig).content(body));
    }

    private ResultActions deliver(String body) throws Exception {
        return deliver(body, signature.sign(body.getBytes(UTF_8)));
    }

    private String statusOf(PlacedOrder order) throws Exception {
        return body(mvc.perform(as(order.token(), get("/api/orders/{id}", order.orderId())))).get("status").asText();
    }

    @Test
    void succeededPaymentMarksOrderPaid() throws Exception {
        PlacedOrder order = placeOrder();

        deliver(event(unique("evt"), "payment.succeeded", order.orderId(), order.totalCents(), clock.instant()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));

        assertThat(statusOf(order)).isEqualTo("PAID");
        assertThat(stockOf(order.productId())).isEqualTo(3);
    }

    @Test
    void badOrMissingSignatureIsRejectedAndChangesNothing() throws Exception {
        PlacedOrder order = placeOrder();
        String body = event(unique("evt"), "payment.succeeded", order.orderId(), order.totalCents(), clock.instant());
        String tampered = body.replace("payment.succeeded", "payment.failed  ");

        deliver(tampered, signature.sign(body.getBytes(UTF_8)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid webhook signature"));
        deliver(body, "00".repeat(32)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Missing X-Signature header"));

        assertThat(statusOf(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(stockOf(order.productId())).isEqualTo(3);
    }

    @Test
    void staleEventIsRejectedEvenWithAValidSignature() throws Exception {
        PlacedOrder order = placeOrder();
        Instant sixMinutesAgo = clock.instant().minus(Duration.ofMinutes(6));

        deliver(event(unique("evt"), "payment.succeeded", order.orderId(), order.totalCents(), sixMinutesAgo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Event timestamp is outside the 5 minute tolerance"));

        assertThat(statusOf(order)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void failedPaymentCancelsOrderAndReleasesStockExactlyOnceAcrossRedeliveries() throws Exception {
        PlacedOrder order = placeOrder();
        String body = event(unique("evt"), "payment.failed", order.orderId(), 0, clock.instant());

        deliver(body).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PROCESSED"));
        assertThat(statusOf(order)).isEqualTo("CANCELLED");
        assertThat(stockOf(order.productId())).isEqualTo(5);

        // The provider didn't see our 200 and sends the same event again.
        clock.advance(Duration.ofMinutes(1));
        deliver(body).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("DUPLICATE"));
        assertThat(stockOf(order.productId())).isEqualTo(5); // not 7
    }

    @Test
    void paidOrderCannotBeCancelledByALaterFailureEvent() throws Exception {
        PlacedOrder order = placeOrder();
        deliver(event(unique("evt"), "payment.succeeded", order.orderId(), order.totalCents(), clock.instant()))
                .andExpect(status().isOk());

        deliver(event(unique("evt"), "payment.failed", order.orderId(), 0, clock.instant()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Can't cancel order " + order.orderId() + " in status PAID"));

        assertThat(statusOf(order)).isEqualTo("PAID");
        assertThat(stockOf(order.productId())).isEqualTo(3);
    }

    @Test
    void paymentForTheWrongAmountDoesNotMarkOrderPaid() throws Exception {
        PlacedOrder order = placeOrder();

        deliver(event(unique("evt"), "payment.succeeded", order.orderId(), order.totalCents() - 1, clock.instant()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Paid amount 2499 doesn't match order total 2500"));

        assertThat(statusOf(order)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void unknownEventTypesAreAcknowledgedAndMalformedOnesRejected() throws Exception {
        PlacedOrder order = placeOrder();
        deliver(event(unique("evt"), "customer.updated", order.orderId(), 0, clock.instant()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));

        deliver("{\"type\":\"payment.succeeded\",\"created\":" + clock.instant().getEpochSecond() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("data must not be null; id must not be blank"));
        deliver("not json").andExpect(status().isBadRequest());

        assertThat(statusOf(order)).isEqualTo("PENDING_PAYMENT");
    }
}
