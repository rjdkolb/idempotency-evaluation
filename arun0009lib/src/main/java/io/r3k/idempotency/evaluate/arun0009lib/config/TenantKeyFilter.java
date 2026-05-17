package io.r3k.idempotency.evaluate.arun0009lib.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Scopes idempotency by tenant. After Spring Security has populated the security context, this
 * filter rewrites the {@code Idempotency-Key} header to {@code <username>:<originalKey>}. The
 * {@code @Idempotent} aspect on {@code OrderController#createOrderIdempotent} reads the header
 * itself, so the rewrite must happen before the controller is invoked. A blank/missing original
 * value is passed through unchanged so the existing {@code MissingIdempotencyKeyException} path
 * still triggers.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantKeyFilter extends OncePerRequestFilter {

  static final String HEADER = "Idempotency-Key";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String original = request.getHeader(HEADER);
    if (auth == null || !auth.isAuthenticated() || original == null || original.isBlank()) {
      chain.doFilter(request, response);
      return;
    }
    String scoped = auth.getName() + ":" + original;
    chain.doFilter(new ScopedKeyRequest(request, scoped), response);
  }

  private static final class ScopedKeyRequest extends HttpServletRequestWrapper {
    private final String scopedValue;

    ScopedKeyRequest(HttpServletRequest delegate, String scopedValue) {
      super(delegate);
      this.scopedValue = scopedValue;
    }

    @Override
    public String getHeader(String name) {
      if (HEADER.equalsIgnoreCase(name)) {
        return scopedValue;
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (HEADER.equalsIgnoreCase(name)) {
        return Collections.enumeration(List.of(scopedValue));
      }
      return super.getHeaders(name);
    }
  }
}
