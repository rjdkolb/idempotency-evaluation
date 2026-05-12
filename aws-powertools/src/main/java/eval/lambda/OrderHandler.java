package eval.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import eval.lambda.dto.CreateOrderRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Handles {@code GET /orders} and {@code POST /orders} (the non-idempotent routes). */
public class OrderHandler
    implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final DynamoDbClient DDB =
      DynamoDbClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();

  private static final OrdersService SERVICE =
      new OrdersService(DDB, System.getenv("ORDERS_TABLE"));

  @Override
  public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
    var http = event.getRequestContext().getHttp();
    var route = http.getMethod() + " " + http.getPath();
    try {
      return switch (route) {
        case "GET /orders" -> json(200, SERVICE.listOrders());
        case "POST /orders" -> {
          var req = MAPPER.readValue(event.getBody(), CreateOrderRequest.class);
          yield json(200, SERVICE.createOrder(req, parseDelay(event)));
        }
        default -> json(404, Map.of("error", "Not Found", "path", http.getPath()));
      };
    } catch (OrdersService.OrderAlreadyExistsException e) {
      var body = new LinkedHashMap<String, Object>();
      body.put("detail", e.getMessage());
      body.put("instance", "/orders");
      body.put("status", 409);
      body.put("title", "Order Already Exists");
      body.put("customerOrderReference", e.getCustomerOrderReference());
      return json(409, body);
    } catch (Exception e) {
      context.getLogger().log("ERROR: " + e);
      return json(
          500,
          Map.of("error", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage())));
    }
  }

  static long parseDelay(APIGatewayV2HTTPEvent event) {
    var params = event.getQueryStringParameters();
    if (params == null) return 10_000L;
    var v = params.get("delayMillis");
    return v == null ? 10_000L : Long.parseLong(v);
  }

  static APIGatewayV2HTTPResponse json(int status, Object body) {
    try {
      return APIGatewayV2HTTPResponse.builder()
          .withStatusCode(status)
          .withHeaders(Map.of("Content-Type", "application/json"))
          .withBody(MAPPER.writeValueAsString(body))
          .build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
