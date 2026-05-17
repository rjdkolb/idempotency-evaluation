package io.r3k.idempotency.evaluate.arun0009lib.order;

public record OrderResponse(String customerOrderReference, String supplierOrderReference) {
  static OrderResponse from(CustomerOrder order) {
    return new OrderResponse(order.getCustomerOrderReference(), order.getSupplierOrderReference());
  }
}
