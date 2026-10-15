package edu.campus.studyroom.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.Arrays;

/**
 * Stateless JWT security. Default is authenticated; only login, public GET notices
 * and the minimal health endpoint are whitelisted. /admin/** requires ROLE_ADMIN.
 * Swagger is open on the local profile only, admin-only otherwise.
 * CSRF is disabled because credentials travel in the Authorization header, not cookies.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionValidationFilter sessionValidationFilter,
                                           ObjectMapper objectMapper, BusinessClock clock) throws Exception {
        boolean localProfile = Arrays.asList(environment.getActiveProfiles()).contains("local");

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/v1/auth/login").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/notices", "/api/v1/notices/{id}").permitAll();
                    auth.requestMatchers("/actuator/health").permitAll();
                    if (localProfile) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    } else {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .hasRole("ADMIN");
                    }
                    auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        // 必须显式声明 jwt()，否则 Spring Security 无法确定 bearer 令牌格式
                        .jwt(jwt -> {
                        })
                        .authenticationEntryPoint(authenticationEntryPoint(clock))
                        .accessDeniedHandler(accessDeniedHandler(clock)))
                .addFilterAfter(sessionValidationFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint(BusinessClock clock) {
        return (request, response, authException) ->
                write(response, HttpServletResponse.SC_UNAUTHORIZED,
                        ErrorEnvelope.of(ErrorCode.AUTH_REQUIRED, "请先登录"), clock);
    }

    private AccessDeniedHandler accessDeniedHandler(BusinessClock clock) {
        return (request, response, accessDeniedException) ->
                write(response, HttpServletResponse.SC_FORBIDDEN,
                        ErrorEnvelope.of(ErrorCode.FORBIDDEN, "无权限执行该操作"), clock);
    }

    private void write(HttpServletResponse response, int status, ErrorEnvelope envelope, BusinessClock clock)
            throws IOException {
        String traceId = MDC.get("traceId");
        ErrorEnvelope withServerTime = new ErrorEnvelope(envelope.code(), envelope.message(), null,
                traceId == null ? "" : traceId, clock.nowOffset().toString(), null);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(new ObjectMapper().writeValueAsString(withServerTime));
    }
}
