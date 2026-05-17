package eval.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** Shared API Gateway response builders and request helpers used by both Lambda handlers. */
final class LambdaResponses {

  static final ObjectMapper MAPPER = new ObjectMapper();

  private LambdaResponses() {}

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

  static APIGatewayV2HTTPResponse unauthorized() {
    try {
      return APIGatewayV2HTTPResponse.builder()
          .withStatusCode(401)
          .withHeaders(
              Map.of(
                  "Content-Type", "application/json",
                  "WWW-Authenticate", "Basic realm=\"orders\""))
          .withBody(
              MAPPER.writeValueAsString(
                  Map.of(
                      "detail", "Missing or invalid HTTP Basic credentials",
                      "status", 401,
                      "title", "Unauthorized")))
          .build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static long parseDelay(APIGatewayV2HTTPEvent event) {
    var params = event.getQueryStringParameters();
    if (params == null) return 10_000L;
    var v = params.get("delayMillis");
    return v == null ? 10_000L : Long.parseLong(v);
  }
}
