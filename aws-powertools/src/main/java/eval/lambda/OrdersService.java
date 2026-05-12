package eval.lambda;

import eval.lambda.dto.CreateOrderRequest;
import eval.lambda.dto.OrderResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.lambda.powertools.idempotency.IdempotencyKey;
import software.amazon.lambda.powertools.idempotency.Idempotent;

@RequiredArgsConstructor
public class OrdersService {

  private final DynamoDbClient ddb;
  private final String ordersTable;

  /** Create an order, rejecting duplicates by customerOrderReference. */
  public OrderResponse createOrder(CreateOrderRequest request, long delayMillis) {
    sleep(delayMillis);
    var supplierRef = "SUP-" + UUID.randomUUID();
    try {
      putOrder(request.customerOrderReference(), supplierRef, true);
    } catch (ConditionalCheckFailedException e) {
      throw new OrderAlreadyExistsException(request.customerOrderReference());
    }
    return new OrderResponse(request.customerOrderReference(), supplierRef);
  }

  /** Create an order idempotently — Powertools' {@code @Idempotent} handles deduplication. */
  @Idempotent
  public OrderResponse createOrderIdempotent(
      @IdempotencyKey String idempotencyKey, CreateOrderRequest request, long delayMillis) {
    sleep(delayMillis);
    var supplierRef = "SUP-" + UUID.randomUUID();
    putOrder(request.customerOrderReference(), supplierRef, false);
    return new OrderResponse(request.customerOrderReference(), supplierRef);
  }

  public List<OrderResponse> listOrders() {
    return ddb.scan(ScanRequest.builder().tableName(ordersTable).build()).items().stream()
        .map(
            item ->
                new OrderResponse(
                    item.get("customerOrderReference").s(), item.get("supplierOrderReference").s()))
        .toList();
  }

  private void putOrder(String customerRef, String supplierRef, boolean rejectDuplicates) {
    var builder =
        PutItemRequest.builder()
            .tableName(ordersTable)
            .item(
                Map.of(
                    "customerOrderReference", AttributeValue.fromS(customerRef),
                    "supplierOrderReference", AttributeValue.fromS(supplierRef)));
    if (rejectDuplicates) {
      builder.conditionExpression("attribute_not_exists(customerOrderReference)");
    }
    ddb.putItem(builder.build());
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

  @Getter
  public static class OrderAlreadyExistsException extends RuntimeException {
    private final String customerOrderReference;

    public OrderAlreadyExistsException(String customerOrderReference) {
      super("Order already exists for customerOrderReference: " + customerOrderReference);
      this.customerOrderReference = customerOrderReference;
    }
  }
}
