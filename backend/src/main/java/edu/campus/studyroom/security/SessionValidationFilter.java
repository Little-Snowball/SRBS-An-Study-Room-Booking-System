package edu.campus.studyroom.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campus.studyroom.common.BusinessClock;
import edu.campus.studyroom.common.ErrorCode;
import edu.campus.studyroom.common.ErrorEnvelope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Runs after the bearer-token filter. Re-reads the session and user from the
 * database on every request: revoked sessions, disabled/blocked accounts and
 * bumped auth_version take effect immediately.
 * Accounts with must_change_password may only call change-password and logout.
 */
@Component
public class SessionValidationFilter extends OncePerRequestFilter {

    private static final List<String> PASSWORD_CHANGE_ALLOWED = List.of(
            "/api/v1/auth/change-password", "/api/v1/auth/logout");

    private final SessionValidator sessionValidator;
    private final ObjectMapper objectMapper;
    private final BusinessClock clock;

    public SessionValidationFilter(SessionValidator sessionValidator, ObjectMapper objectMapper,
                                   BusinessClock clock) {
        this.sessionValidator = sessionValidator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            try {
                Jwt jwt = (Jwt) jwtAuth.getPrincipal();
                String sid = jwt.getClaimAsString(JwtTokenService.CLAIM_SID);
                Integer ver = jwt.getClaim(JwtTokenService.CLAIM_VER);
                CurrentUserPrincipal principal = sessionValidator.validate(sid, ver == null ? -1 : ver);
                if (principal == null) {
                    SecurityContextHolder.clearContext();
                } else if (principal.mustChangePassword()
                        && !PASSWORD_CHANGE_ALLOWED.contains(request.getRequestURI())) {
                    writeEnvelope(response, HttpServletResponse.SC_FORBIDDEN,
                            buildEnvelope(ErrorCode.PASSWORD_CHANGE_REQUIRED, "请先修改初始密码"));
                    return;
                } else {
                    List<GrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
                    JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities,
                            String.valueOf(principal.userId()));
                    token.setDetails(principal);
                    SecurityContextHolder.getContext().setAuthentication(token);
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void writeEnvelope(HttpServletResponse response, int status, ErrorEnvelope envelope)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), envelope);
    }

    private ErrorEnvelope buildEnvelope(ErrorCode code, String message) {
        String traceId = MDC.get("traceId");
        return new ErrorEnvelope(code.name(), message, null, traceId == null ? "" : traceId,
                clock.nowOffset().toString(), null);
    }
}
