package net.datasa.project01.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

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

    @Value("${jwt.issuer:MatchTalk}")
    private String issuer;

    @Value("${jwt.expected-type:access}")
    private String expectedTokenType;
    
    // TODO: Refresh Token 지원을 위한 설정 추가
    // @Value("${jwt.refresh.expiration:604800000}") // 7일 기본값
    // private long REFRESH_EXPIRATION_TIME;

    private Key key;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(SECRET_KEY)) {
            throw new IllegalArgumentException("JWT secret key must not be empty");
        }

        byte[] keyBytes = resolveKeyBytes(SECRET_KEY);
        if (keyBytes.length < 32) {
            logger.warn("JWT secret key length is {} bytes which is below the recommended 32 bytes for HS256. "
                    + "Falling back to SecretKeySpec for compatibility with jsonwebtoken-generated tokens.", keyBytes.length);
            key = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
        } else {
            key = Keys.hmacShaKeyFor(keyBytes);
        }

        if (!StringUtils.hasText(issuer)) {
            issuer = null; // treat empty as not enforced
        }

        if (!StringUtils.hasText(expectedTokenType)) {
            expectedTokenType = null;
        }

        logger.info("JwtUtil initialized with expiration time: {} ms, issuer: {}, expected token type: {}", EXPIRATION_TIME,
                issuer, expectedTokenType);
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
                .setIssuer(issuer != null ? issuer : "MatchTalk") // 토큰 발급자
                .claim("type", expectedTokenType != null ? expectedTokenType : "access") // 토큰 타입
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String subject = claims.getSubject();
            if (!StringUtils.hasText(subject)) {
                throw new IllegalArgumentException("JWT token does not contain a subject (sub)");
            }
            return subject;
        } catch (Exception e) {
            logger.error("Error extracting username from token", e);
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);

            if (expectedTokenType != null) {
                String tokenType = claims.get("type", String.class);
                if (!expectedTokenType.equals(tokenType)) {
                    logger.warn("JWT token type mismatch. expected={}, actual={}", expectedTokenType, tokenType);
                    return false;
                }
            }

            if (issuer != null) {
                String tokenIssuer = claims.getIssuer();
                if (!issuer.equals(tokenIssuer)) {
                    logger.warn("JWT token issuer mismatch. expected={}, actual={}", issuer, tokenIssuer);
                    return false;
                }
            }

            if (!StringUtils.hasText(claims.getSubject())) {
                logger.warn("JWT token subject (sub) claim is empty");
                return false;
            }

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

    private Claims parseClaims(String token) {
        JwtParserBuilder builder = Jwts.parserBuilder()
                .setSigningKey(key);

        if (issuer != null) {
            builder.requireIssuer(issuer);
        }

        return builder.build()
                .parseClaimsJws(token)
                .getBody();
    }

    private byte[] resolveKeyBytes(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
            logger.info("JWT secret treated as BASE64 encoded value ({} bytes after decoding)", keyBytes.length);
        } catch (IllegalArgumentException ex) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            logger.info("JWT secret treated as plain text value ({} bytes)", keyBytes.length);
        }
        return keyBytes;
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
