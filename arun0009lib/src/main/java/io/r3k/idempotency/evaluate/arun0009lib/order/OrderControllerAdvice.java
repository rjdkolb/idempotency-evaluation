package io.r3k.idempotency.evaluate.arun0009lib.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderControllerAdvice {

  @ExceptionHandler(OrderAlreadyExistsException.class)
  public ProblemDetail handleOrderAlreadyExists(OrderAlreadyExistsException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Order Already Exists");
    problem.setProperty("customerOrderReference", ex.getCustomerOrderReference());
    return problem;
  }

  @ExceptionHandler(MissingIdempotencyKeyException.class)
  public ProblemDetail handleMissingIdempotencyKey(MissingIdempotencyKeyException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setTitle("Missing Idempotency-Key");
    return problem;
  }
}
