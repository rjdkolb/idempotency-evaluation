package eval.lambda;

import static eval.lambda.OrderHandler.json;
import static eval.lambda.OrderHandler.parseDelay;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import eval.lambda.dto.CreateOrderRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.lambda.powertools.idempotency.Idempotency;
import software.amazon.lambda.powertools.idempotency.IdempotencyConfig;
import software.amazon.lambda.powertools.idempotency.exceptions.IdempotencyAlreadyInProgressException;
import software.amazon.lambda.powertools.idempotency.persistence.dynamodb.DynamoDBPersistenceStore;

/**
 * Handles {@code POST /orders/idempotent}.
 *
 * <p>Idempotency is provided by Powertools' {@code @Idempotent} annotation on {@link
 * OrdersService#createOrderIdempotent}.
 *
 * <p>Unlike {@code arun0009lib} which blocks in-flight duplicates until the first call completes,
 * Powertools rejects them immediately with {@link IdempotencyAlreadyInProgressException} — surfaced
 * here as {@code 409 Conflict}.
 */
public class IdempotentOrderHandler
    implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final DynamoDbClient DDB =
      DynamoDbClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();

  private static final OrdersService SERVICE =
      new OrdersService(DDB, System.getenv("ORDERS_TABLE"));

  static {
    Idempotency.config()
        .withPersistenceStore(
            DynamoDBPersistenceStore.builder()
                .withTableName(System.getenv("IDEMPOTENCY_TABLE"))
                .build())
        .withConfig(
            IdempotencyConfig.builder().withExpiration(java.time.Duration.ofHours(1)).build())
        .configure();
  }

  @Override
  public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
    try {
      var req = MAPPER.readValue(event.getBody(), CreateOrderRequest.class);
      var key = idempotencyKey(event);
      var delay = parseDelay(event);
      return json(200, SERVICE.createOrderIdempotent(key, req, delay));
    } catch (IdempotencyAlreadyInProgressException e) {
      return json(
          409,
          Map.of(
              "detail", "Request with this idempotency key is already in progress",
              "status", 409,
              "title", "Idempotent Request In Progress"));
    } catch (Exception e) {
      context.getLogger().log("ERROR: " + e);
      return json(
          500,
          Map.of(
              "error", e.getClass().getSimpleName(),
              "message", String.valueOf(e.getMessage())));
    }
  }

  /** Use the Idempotency-Key header, falling back to a SHA-256 of the body. */
  private static String idempotencyKey(APIGatewayV2HTTPEvent event) {
    if (event.getHeaders() != null) {
      for (var entry : event.getHeaders().entrySet()) {
        if (entry.getKey().equalsIgnoreCase("Idempotency-Key")
            && entry.getValue() != null
            && !entry.getValue().isBlank()) {
          return entry.getValue();
        }
      }
    }
    return sha256(event.getBody());
  }

  private static String sha256(String input) {
    try {
      var bytes =
          MessageDigest.getInstance("SHA-256")
              .digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
