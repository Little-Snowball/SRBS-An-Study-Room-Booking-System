package edu.campus.studyroom.auth.controller;

import edu.campus.studyroom.auth.dto.ChangePasswordRequest;
import edu.campus.studyroom.auth.dto.LoginRequest;
import edu.campus.studyroom.auth.dto.LoginResponse;
import edu.campus.studyroom.auth.service.AuthService;
import edu.campus.studyroom.common.ApiResponse;
import edu.campus.studyroom.security.CurrentUser;
import edu.campus.studyroom.security.CurrentUserPrincipal;
import edu.campus.studyroom.security.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. /login is public; /logout and /change-password
 * require a bearer token (enforced by SecurityConfig + SessionValidationFilter).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        return ApiResponse.ok(authService.login(request.account(), request.password(), ip));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth
                && jwtAuth.getPrincipal() instanceof Jwt jwt) {
            authService.logout(jwt.getClaimAsString(JwtTokenService.CLAIM_SID));
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            @CurrentUser CurrentUserPrincipal principal) {
        authService.changePassword(principal.userId(), request.oldPassword(),
                request.newPassword());
        return ApiResponse.ok(null);
    }

    /** First hop of X-Forwarded-For when present, otherwise the socket address. */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
