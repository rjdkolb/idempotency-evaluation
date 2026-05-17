package io.r3k.idempotency.evaluate.arun0009lib.order;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

  private final OrderRepository orderRepository;

  public List<OrderResponse> listOrders(String tenant) {
    return orderRepository.findAllByTenantOrderByIdAsc(tenant).stream()
        .map(OrderResponse::from)
        .toList();
  }

  @Transactional
  public OrderResponse createOrder(String tenant, CreateOrderRequest request, long delayMillis) {
    var customerOrderReference = request.customerOrderReference();
    sleep(delayMillis);
    var newOrder = new CustomerOrder(tenant, customerOrderReference, "SUP-" + UUID.randomUUID());
    try {
      return OrderResponse.from(orderRepository.saveAndFlush(newOrder));
    } catch (DataIntegrityViolationException ex) {
      throw new OrderAlreadyExistsException(customerOrderReference);
    }
  }

  private static void sleep(long millis) {
    if (millis <= 0) return;
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Order creation interrupted", e);
    }
  }
}
