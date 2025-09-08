package net.datasa.project01.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.Key;
import java.util.Date;

import org.slf4j.Logger; 
import org.slf4j.LoggerFactory; 

/**
 * JWT -> 사용자가 아이디와 비밀번호로 로그인에 성공했을 때 발급하는 토큰(신분증과 비슷함)
 * 이 사용자는 누구인가? 언제까지 유효한가에 대한 정보가 들어있음.
 * 서버가 각 사용자의 로그인 상태를 일일이 기억할 필요 없이 토큰만으로 인증을 처리 할 수 있는 REST API
 */

@Component
public class JwtUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    @Value("${jwt.secret}")
    private String SECRET_KEY;
    
    @Value("${jwt.expiration:86400000}") // 24시간 기본값
    private long EXPIRATION_TIME;
    
    // TODO: Refresh Token 지원을 위한 설정 추가
    // @Value("${jwt.refresh.expiration:604800000}") // 7일 기본값
    // private long REFRESH_EXPIRATION_TIME;

    private Key key;

    @PostConstruct
    public void init() {
        if (SECRET_KEY.length() < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 32 characters long");
        }
        key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
        logger.info("JwtUtil initialized with expiration time: {} ms", EXPIRATION_TIME);
    }

    public String createToken(String username) {
        // TODO: 사용자 역할(Role) 정보도 토큰에 포함 고려
        // TODO: 디바이스 정보 추가 고려
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .setIssuer("MatchTalk") // 토큰 발급자
                .claim("type", "access") // 토큰 타입
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
                
            return claims.getSubject();
        } catch (Exception e) {
            logger.error("Error extracting username from token", e);
            throw new IllegalArgumentException("Invalid token", e);
        }
    }
    
    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            // TODO: 추가 유효성 검사 (블랙리스트 체크 등)
            return true;
        } catch (SignatureException e) {
            logger.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.debug("JWT token is expired: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("JWT claims string is empty: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during token validation", e);
        }
        return false;
    }
    
    // TODO: 추가 필요한 메서드들
    // public String createRefreshToken(String username) { }
    // public boolean validateRefreshToken(String token) { }
    // public String refreshAccessToken(String refreshToken) { }
    // public void invalidateToken(String token) { } // 블랙리스트 추가
    // public Date getExpirationDateFromToken(String token) { }
    // public boolean isTokenExpired(String token) { }
    // public List<String> getRolesFromToken(String token) { }
}
