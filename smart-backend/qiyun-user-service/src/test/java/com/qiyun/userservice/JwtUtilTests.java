package com.qiyun.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qiyun.userservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtUtilTests {

    private static final String TEST_SECRET = "testSecretKeyForTestingPurposesOnly123456789";
    private static final String OTHER_SECRET = "otherSecretKeyForTestingPurposesOnly12345678";

    private JwtUtil jwtUtil;
    private Key testKey;
    private Key otherKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, 604800000L);
        testKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("生成Token并解析正确的用户ID")
    void generateToken_andExtractUsername() {
        String token = jwtUtil.generateToken("20250001", "STUDENT");

        String extractedUsername = jwtUtil.extractUsername(token);

        assertThat(extractedUsername).isEqualTo("20250001");
    }

    @Test
    @DisplayName("Token包含角色claim")
    void tokenContainsRoleClaim() {
        String token = jwtUtil.generateToken("20250001", "STUDENT");

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(testKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.get("role")).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("合法Token验证成功")
    void validateToken_validToken() {
        String token = jwtUtil.generateToken("20250001", "STUDENT");

        boolean isValid = jwtUtil.validateToken(token, "20250001");

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("用户ID不匹配时验证失败")
    void validateToken_wrongUsername() {
        String token = jwtUtil.generateToken("20250001", "STUDENT");

        boolean isValid = jwtUtil.validateToken(token, "20250002");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("被篡改的Token验证失败")
    void validateToken_tamperedToken() {
        String token = jwtUtil.generateToken("20250001", "STUDENT");
        String tamperedToken = token.substring(0, 10) + "X" + token.substring(11);

        assertThatThrownBy(() -> jwtUtil.extractUsername(tamperedToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("格式错误的Token解析失败")
    void extractUsername_malformedToken() {
        String malformedToken = "not.a.valid.token";

        assertThatThrownBy(() -> jwtUtil.extractUsername(malformedToken))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    @DisplayName("空Token解析失败")
    void extractUsername_emptyToken() {
        assertThatThrownBy(() -> jwtUtil.extractUsername(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nullToken解析失败")
    void extractUsername_nullToken() {
        assertThatThrownBy(() -> jwtUtil.extractUsername(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("使用不同密钥签发的Token验证失败")
    void validateToken_wrongKey() {
        String otherToken = Jwts.builder()
                .setSubject("20250001")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 604800000L))
                .claim("role", "STUDENT")
                .signWith(otherKey, SignatureAlgorithm.HS256)
                .compact();

        assertThatThrownBy(() -> jwtUtil.extractUsername(otherToken))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    @DisplayName("过期Token被拒绝")
    void validateToken_expiredToken() {
        String expiredToken = Jwts.builder()
                .setSubject("20250001")
                .setIssuedAt(new Date(System.currentTimeMillis() - 2000))
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .claim("role", "STUDENT")
                .signWith(testKey, SignatureAlgorithm.HS256)
                .compact();

        assertThatThrownBy(() -> jwtUtil.extractUsername(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("Token包含签发时间")
    void tokenContainsIssuedAt() {
        long beforeTime = System.currentTimeMillis();
        String token = jwtUtil.generateToken("20250001", "STUDENT");
        long afterTime = System.currentTimeMillis();

        Date issuedAt = jwtUtil.extractExpiration(token);

        assertThat(issuedAt).isNotNull();
        assertThat(issuedAt.getTime()).isGreaterThan(beforeTime);
        assertThat(issuedAt.getTime()).isLessThanOrEqualTo(afterTime + 604800000L);
    }

    @Test
    @DisplayName("Token过期时间正确设置")
    void tokenExpirationTime() {
        String token = jwtUtil.generateToken("20250001", "STUDENT");

        Date expiration = jwtUtil.extractExpiration(token);
        Date now = new Date();

        assertThat(expiration).isAfter(now);

        long expectedExpiry = System.currentTimeMillis() + 604800000L;
        long actualExpiry = expiration.getTime();
        assertThat(Math.abs(actualExpiry - expectedExpiry)).isLessThan(10000);
    }

    @Test
    @DisplayName("extractClaim方法正常工作")
    void extractClaim_works() {
        String token = jwtUtil.generateToken("20250001", "STUDENT");

        String username = jwtUtil.extractClaim(token, Claims::getSubject);

        assertThat(username).isEqualTo("20250001");
    }
}