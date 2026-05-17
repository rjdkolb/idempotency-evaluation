package io.r3k.idempotency.evaluate.arun0009lib.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP Basic Auth with the username acting as a tenant marker. Any non-blank username with any
 * password is accepted; the password is not validated. Unauthenticated requests get {@code 401
 * WWW-Authenticate: Basic} before any controller runs, which means the {@code @Idempotent} aspect
 * never executes for them and no cached response can leak.
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .httpBasic(basic -> basic.realmName("orders"))
        .authenticationProvider(tenantAuthenticationProvider())
        .build();
  }

  @Bean
  public AuthenticationProvider tenantAuthenticationProvider() {
    return new AuthenticationProvider() {
      @Override
      public Authentication authenticate(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        String username = principal == null ? null : principal.toString();
        if (username == null || username.isBlank()) {
          throw new BadCredentialsException("Username must not be blank");
        }
        return UsernamePasswordAuthenticationToken.authenticated(username, null, List.of());
      }

      @Override
      public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
      }
    };
  }
}
