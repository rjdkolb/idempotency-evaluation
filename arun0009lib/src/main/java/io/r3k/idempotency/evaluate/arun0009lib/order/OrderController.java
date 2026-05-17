package io.r3k.idempotency.evaluate.arun0009lib.order;

import io.github.arun0009.idempotent.core.annotation.Idempotent;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;

  @GetMapping
  public List<OrderResponse> listOrders(Authentication auth) {
    return orderService.listOrders(auth.getName());
  }

  @PostMapping
  public OrderResponse createOrder(
      Authentication auth,
      @Valid @RequestBody CreateOrderRequest request,
      @RequestParam(defaultValue = "10000") long delayMillis) {
    return orderService.createOrder(auth.getName(), request, delayMillis);
  }

  @PostMapping("/idempotent")
  @Idempotent(duration = "PT1H", hashKey = true)
  public OrderResponse createOrderIdempotent(
      Authentication auth,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreateOrderRequest request,
      @RequestParam(defaultValue = "10000") long delayMillis) {
    if (idempotencyKey.isBlank()) {
      throw new MissingIdempotencyKeyException();
    }
    return orderService.createOrder(auth.getName(), request, delayMillis);
  }
}
