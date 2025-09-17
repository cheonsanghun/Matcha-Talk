package net.datasa.project01.config;

import net.datasa.project01.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StompHandlerTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private StompHandler stompHandler;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("핸드셰이크에서 인증된 Principal이 있으면 그대로 사용한다")
    void preSend_withHandshakePrincipal_shouldReuseAuthentication() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "tester",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        accessor.setUser(authentication);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = stompHandler.preSend(message, messageChannel);

        assertThat(result).isSameAs(message);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(authentication);
        assertThat(accessor.getUser()).isEqualTo(authentication);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 CONNECT 요청을 거부한다")
    void preSend_withoutAuthorizationHeader_shouldThrowException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-2");

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> stompHandler.preSend(message, messageChannel));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("유효하지 않은 JWT 토큰은 CONNECT 단계에서 거부한다")
    void preSend_withInvalidToken_shouldThrowBadCredentials() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-3");
        accessor.addNativeHeader("Authorization", "Bearer invalid");

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        given(jwtUtil.validateToken("invalid")).willReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> stompHandler.preSend(message, messageChannel));

        verify(jwtUtil).validateToken(eq("invalid"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("유효한 JWT 토큰은 CONNECT 요청을 인증한다")
    void preSend_withValidToken_shouldAuthenticate() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-4");
        accessor.addNativeHeader("Authorization", "Bearer valid");

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        given(jwtUtil.validateToken("valid")).willReturn(true);
        given(jwtUtil.getUsernameFromToken("valid")).willReturn("tester");

        UserDetails userDetails = User.withUsername("tester")
                .password("password")
                .authorities("ROLE_USER")
                .build();
        given(userDetailsService.loadUserByUsername("tester")).willReturn(userDetails);

        Message<?> result = stompHandler.preSend(message, messageChannel);

        assertThat(result).isSameAs(message);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("tester");
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    }
}
