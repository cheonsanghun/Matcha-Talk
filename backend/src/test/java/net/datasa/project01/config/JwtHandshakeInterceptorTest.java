package net.datasa.project01.config;

import net.datasa.project01.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private JwtHandshakeInterceptor interceptor;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("JWT가 누락된 핸드셰이크는 401 상태로 거절된다")
    void beforeHandshake_withoutToken_shouldReject() {
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "/ws-stomp");
        ServletServerHttpRequest request = new ServletServerHttpRequest(httpServletRequest);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(httpServletResponse);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        assertThat(httpServletResponse.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtUtil, userDetailsService);
    }

    @Test
    @DisplayName("유효하지 않은 JWT는 핸드셰이크 단계에서 거절된다")
    void beforeHandshake_withInvalidToken_shouldReject() {
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "/ws-stomp");
        httpServletRequest.setQueryString("token=invalid-token");
        httpServletRequest.addParameter("token", "invalid-token");

        ServletServerHttpRequest request = new ServletServerHttpRequest(httpServletRequest);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(httpServletResponse);

        given(jwtUtil.validateToken("invalid-token")).willReturn(false);

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        assertThat(httpServletResponse.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("유효한 JWT는 인증 정보를 세션 속성에 저장한다")
    void beforeHandshake_withValidToken_shouldSucceed() {
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest("GET", "/ws-stomp");
        httpServletRequest.addHeader("Authorization", "Bearer valid-token");

        ServletServerHttpRequest request = new ServletServerHttpRequest(httpServletRequest);
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(httpServletResponse);

        given(jwtUtil.validateToken("valid-token")).willReturn(true);
        given(jwtUtil.getUsernameFromToken("valid-token")).willReturn("tester");
        UserDetails userDetails = User.withUsername("tester")
                .password("password")
                .authorities("ROLE_USER")
                .build();
        given(userDetailsService.loadUserByUsername("tester")).willReturn(userDetails);

        HashMap<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertThat(result).isTrue();
        assertThat(httpServletResponse.getStatus()).isEqualTo(200);
        assertThat(attributes)
                .containsKey(JwtHandshakeInterceptor.WEBSOCKET_PRINCIPAL_ATTR);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("tester");

        interceptor.afterHandshake(request, response, null, null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
