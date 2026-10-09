package dev.kumarnenavath.shop.web;

import dev.kumarnenavath.shop.security.PublicEndpoint;
import dev.kumarnenavath.shop.service.PaymentWebhookService;
import org.springframework.web.bind.annotation.*;

/** Authenticated by the HMAC signature, not a bearer token. */
@RestController
@RequestMapping("/api/webhooks")
@PublicEndpoint
public class PaymentWebhookController {

    private final PaymentWebhookService webhooks;

    public PaymentWebhookController(PaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    public record WebhookResult(String outcome) {}

    /** Takes the body as raw bytes: the signature is over the exact bytes sent, not a re-serialized copy. */
    @PostMapping("/payments")
    public WebhookResult payment(@RequestBody byte[] body,
                                 @RequestHeader(name = "X-Signature", required = false) String signature) {
        return new WebhookResult(webhooks.receive(body, signature).name());
    }
}
