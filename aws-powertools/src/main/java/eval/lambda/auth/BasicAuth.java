package eval.lambda.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Parses {@code Authorization: Basic …}. The username is the tenant marker; the password is not
 * validated. Returns {@code null} if the header is missing, malformed, or carries a blank username.
 * Handlers MUST return 401 in that case before invoking any service method.
 *
 * This is just for simulating a tenant marker. Not for production.
 */
public final class BasicAuth {

  private BasicAuth() {}

  public static String tenantOrNull(Map<String, String> headers) {
    if (headers == null) return null;
    String header = null;
    for (var entry : headers.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Authorization")) {
        header = entry.getValue();
        break;
      }
    }
    if (header == null || header.isBlank()) return null;
    String prefix = "Basic ";
    if (header.length() <= prefix.length()
        || !header.regionMatches(true, 0, prefix, 0, prefix.length())) {
      return null;
    }
    String b64 = header.substring(prefix.length()).trim();
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(b64);
    } catch (IllegalArgumentException e) {
      return null;
    }
    String creds = new String(decoded, StandardCharsets.UTF_8);
    int colon = creds.indexOf(':');
    String username = colon < 0 ? creds : creds.substring(0, colon);
    return username.isBlank() ? null : username;
  }
}
