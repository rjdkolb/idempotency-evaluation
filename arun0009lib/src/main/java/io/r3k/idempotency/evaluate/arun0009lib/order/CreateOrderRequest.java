package io.r3k.idempotency.evaluate.arun0009lib.order;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(@NotBlank String customerOrderReference) {}
