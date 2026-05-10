package io.r3k.idempotency.evaluate.arun0009lib.order;

public class OrderAlreadyExistsException extends RuntimeException {

  private final String customerOrderReference;

  public OrderAlreadyExistsException(String customerOrderReference) {
    super("Order already exists for customerOrderReference: " + customerOrderReference);
    this.customerOrderReference = customerOrderReference;
  }

  public String getCustomerOrderReference() {
    return customerOrderReference;
  }
}
