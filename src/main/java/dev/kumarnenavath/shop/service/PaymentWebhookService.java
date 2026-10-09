package dev.kumarnenavath.shop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kumarnenavath.shop.ShopProperties;
import dev.kumarnenavath.shop.domain.Order;
import dev.kumarnenavath.shop.domain.OrderLine;
import dev.kumarnenavath.shop.domain.ProcessedEvent;
import dev.kumarnenavath.shop.domain.Product;
import dev.kumarnenavath.shop.error.ConflictException;
import dev.kumarnenavath.shop.error.NotFoundException;
import dev.kumarnenavath.shop.error.UnauthorizedException;
import dev.kumarnenavath.shop.repository.OrderRepository;
import dev.kumarnenavath.shop.repository.ProcessedEventRepository;
import dev.kumarnenavath.shop.repository.ProductRepository;
import dev.kumarnenavath.shop.security.WebhookSignature;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles payment provider callbacks. Order of checks matters:
 * <ol>
 *   <li>signature over the raw bytes, before parsing anything (401)</li>
 *   <li>freshness of the signed {@code created} timestamp, so a captured request can't be replayed later (401)</li>
 *   <li>idempotency by event id: a redelivery returns 200 without touching the order again</li>
 * </ol>
 * The event id is recorded in the same transaction as the order change, so either both happen or neither.
 */
@Service
public class PaymentWebhookService {

    public enum Outcome { PROCESSED, DUPLICATE, IGNORED }

    public record PaymentEvent(@NotBlank String id, @NotBlank String type, @NotNull Long created,
                               @NotNull @Valid Data data) {
        public record Data(@NotNull Long orderId, @NotNull @PositiveOrZero Long amountCents) {}
    }

    static final String SUCCEEDED = "payment.succeeded";
    static final String FAILED = "payment.failed";

    private final WebhookSignature signature;
    private final ObjectMapper json;
    private final Validator validator;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final ProcessedEventRepository events;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration tolerance;

    public PaymentWebhookService(WebhookSignature signature, ObjectMapper json, Validator validator,
                                 OrderRepository orders, ProductRepository products, ProcessedEventRepository events,
                                 PlatformTransactionManager txManager, Clock clock, ShopProperties props) {
        this.signature = signature;
        this.json = json;
        this.validator = validator;
        this.orders = orders;
        this.products = products;
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.tolerance = props.webhook().tolerance();
    }

    public Outcome receive(byte[] rawBody, String signatureHeader) {
        signature.verify(rawBody, signatureHeader);
        PaymentEvent event = parse(rawBody);

        Instant created = Instant.ofEpochSecond(event.created());
        if (Duration.between(created, clock.instant()).abs().compareTo(tolerance) > 0) {
            throw new UnauthorizedException("Event timestamp is outside the " + tolerance.toMinutes() + " minute tolerance");
        }

        try {
            return tx.execute(status -> apply(event));
        } catch (DataIntegrityViolationException e) {
            // A concurrent delivery of the same event inserted the id first; its transaction did the work.
            return Outcome.DUPLICATE;
        }
    }

    private Outcome apply(PaymentEvent event) {
        if (events.existsById(event.id())) return Outcome.DUPLICATE;
        events.saveAndFlush(new ProcessedEvent(event.id(), event.type(), clock.instant()));

        switch (event.type()) {
            case SUCCEEDED -> {
                Order order = order(event);
                if (event.data().amountCents() != order.getTotalCents()) {
                    throw new ConflictException("Paid amount " + event.data().amountCents()
                            + " doesn't match order total " + order.getTotalCents());
                }
                order.markPaid();
            }
            case FAILED -> {
                Order order = order(event);
                if (order.cancel()) releaseStock(order);
            }
            default -> {
                // Providers send many event types; acknowledge the ones we don't use so they aren't retried.
                return Outcome.IGNORED;
            }
        }
        return Outcome.PROCESSED;
    }

    private void releaseStock(Order order) {
        Map<Long, Product> byId = products.findAllById(order.getLines().stream().map(OrderLine::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        for (OrderLine line : order.getLines()) {
            Product product = byId.get(line.getProductId());
            if (product != null) product.release(line.getQuantity());
        }
    }

    private Order order(PaymentEvent event) {
        long orderId = event.data().orderId();
        return orders.findWithLinesById(orderId).orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
    }

    private PaymentEvent parse(byte[] rawBody) {
        PaymentEvent event;
        try {
            event = json.readValue(rawBody, PaymentEvent.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed event payload");
        }
        if (event == null) throw new IllegalArgumentException("Malformed event payload");
        Set<ConstraintViolation<PaymentEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        return event;
    }
}
