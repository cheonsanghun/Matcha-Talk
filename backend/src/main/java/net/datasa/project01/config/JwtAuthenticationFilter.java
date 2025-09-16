package net.datasa.project01.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.exception.JwtAuthenticationException;
import net.datasa.project01.service.UserDetailsServiceImpl;
import net.datasa.project01.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;


@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final List<RequestMatcher> protectedMatchers;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UserDetailsServiceImpl userDetailsService,
            List<RequestMatcher> protectedMatchers) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.protectedMatchers = List.copyOf(protectedMatchers);
    }


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

            if (!StringUtils.hasText(token)) {
                throw JwtAuthenticationException.missingToken();
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                authenticateUser(token, request);
            }

            filterChain.doFilter(request, response);
        } catch (JwtAuthenticationException ex) {
            SecurityContextHolder.clearContext();
            log.warn("JWT 인증 실패: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            log.error("JWT 인증 처리 중 예외 발생", ex);
            throw new ServletException("JWT 인증 처리 중 오류가 발생했습니다.", ex);
        }
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    private void authenticateUser(String token, HttpServletRequest request) {
        if (!jwtUtil.validateToken(token)) {
            throw JwtAuthenticationException.invalidToken();
        }

        String loginId;
        try {
            loginId = jwtUtil.getUsernameFromToken(token);
        } catch (IllegalArgumentException ex) {
            throw JwtAuthenticationException.unreadableToken(ex);
        }

        if (!StringUtils.hasText(loginId)) {
            throw JwtAuthenticationException.invalidToken();
        }

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(loginId);
        } catch (UsernameNotFoundException ex) {
            throw JwtAuthenticationException.userNotFound();
        }

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authentication successful for user: {}", loginId);
    }
}
