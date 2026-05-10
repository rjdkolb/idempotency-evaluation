package io.r3k.idempotency.evaluate.arun0009lib.order;

public class MissingIdempotencyKeyException extends RuntimeException {

  public MissingIdempotencyKeyException() {
    super("Idempotency-Key header is required and must not be blank");
  }
}
