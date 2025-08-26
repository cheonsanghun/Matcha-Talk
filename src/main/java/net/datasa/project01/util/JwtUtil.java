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
    // 비밀 키는 외부에 노출되서는 안되는 키
    @Value("${jwt.secret}")
    private String SECRET_KEY;

    // HMAC-SHA 알고리즘을 위한 키 생성
    private Key key;

    @PostConstruct
    public void init() {
        key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }
    
    // 토큰 유효 시간(24시간)
    private final long EXPIRATION_TIME = 1000L * 60 * 60 * 24;

    /**
     * 사용자 이름을 기반으로 JWT 토큰 생성
     * @param username 사용자 이름
     * @return 생성된 JWT 토큰
     */

    public String createToken(String username) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);

        return Jwts.builder()
                .setSubject(username)       // 토큰 주체
                .setIssuedAt(now)           // 토큰 발급 시간
                .setExpiration(expiration)  // 토큰 만료 시간
                .signWith(key, SignatureAlgorithm.HS256)  //사용할 암호화 알고리즘과 비밀 키
                .compact();
    }

    /**
     * 주어진 토큰에서 사용자 이름을 추출
     * @param token JWT 토큰
     * @return 사용자 이름
     */

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody();
        
        return claims.getSubject();
    }
    
    /**
     * @param token JWT 토큰
     * @return 유효하면 true, 외에는 false
     */
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (SignatureException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

}
