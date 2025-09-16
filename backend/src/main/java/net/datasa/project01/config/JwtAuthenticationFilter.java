package net.datasa.project01.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.service.UserDetailsServiceImpl;
import net.datasa.project01.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final List<RequestMatcher> protectedMatchers;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        boolean requiresAuth = protectedMatchers.stream()
                .anyMatch(matcher -> matcher.matches(request));

        if (!requiresAuth && log.isTraceEnabled()) {
            log.trace("Skipping JWT filter for URI: {}", request.getRequestURI());
        }

        return !requiresAuth;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String token = extractTokenFromRequest(request);

            if (token == null) {
                log.warn("Missing JWT token in Authorization header");
                sendUnauthorizedError(response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                boolean authenticated = authenticateUser(token, request);
                if (!authenticated) {
                    sendUnauthorizedError(response);
                    return;
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication", e);
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    private boolean authenticateUser(String token, HttpServletRequest request) {
        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid JWT token provided");
            return false;
        }

        String loginId = jwtUtil.getUsernameFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authentication successful for user: {}", loginId);
        return true;
    }

    private void sendUnauthorizedError(HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing token");
        }
    }
}
