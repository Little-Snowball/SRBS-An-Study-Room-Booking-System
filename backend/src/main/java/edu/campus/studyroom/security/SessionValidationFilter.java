package edu.campus.studyroom.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 */
@Component
public class SessionValidationFilter extends OncePerRequestFilter {

    private final SessionValidator sessionValidator;

    public SessionValidationFilter(SessionValidator sessionValidator) {
        this.sessionValidator = sessionValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            try {
                Jwt jwt = (Jwt) jwtAuth.getPrincipal();
                String sid = jwt.getClaimAsString(JwtTokenService.CLAIM_SID);
                Object verClaim = jwt.getClaim(JwtTokenService.CLAIM_VER);
                int ver = verClaim instanceof Number n ? n.intValue() : -1;
                CurrentUserPrincipal principal = sessionValidator.validate(sid, ver);
                if (principal == null) {
                    SecurityContextHolder.clearContext();
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
}
