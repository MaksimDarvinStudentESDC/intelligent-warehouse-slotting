package com.warehouse.slotting.security;
import com.warehouse.slotting.config.JwtConfig;
import com.warehouse.slotting.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.time.Instant;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class JwtServiceTest {
    private final JwtConfig config = new JwtConfig();
    private final javax.crypto.SecretKey key = config.jwtSecretKey(Base64.getEncoder().encodeToString(new byte[32]));
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtDecoder decoder = config.jwtDecoder(key);
    @Test void roundTrip() {
        var user = new User("operator", "Test User", "unused", UserRole.operator);
        ReflectionTestUtils.setField(user, "id", 7L);
        var jwt = decoder.decode(new JwtService(encoder, 1800).generateToken(user));
        assertEquals("7", jwt.getSubject());
        assertEquals("operator", jwt.getClaimAsString("login"));
        assertEquals("OPERATOR", jwt.getClaimAsString("role"));
        assertEquals("warehouse-slotting", jwt.getClaimAsString("iss"));
        assertEquals(1800, java.time.Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds());
    }
    private String token(String issuer, Instant expiry, JwtEncoder signer) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("7").issuedAt(expiry.minusSeconds(1800)).expiresAt(expiry).build();
        return signer.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    @Test void expiredTokenRejected() { assertThrows(JwtException.class, () -> decoder.decode(token("warehouse-slotting", Instant.now().minusSeconds(120), encoder))); }
    @Test void wrongIssuerRejected() { assertThrows(JwtException.class, () -> decoder.decode(token("other", Instant.now().plusSeconds(1800), encoder))); }
    @Test void wrongSignatureRejected() {
        byte[] other = new byte[32]; other[0] = 1;
        var signer = config.jwtEncoder(config.jwtSecretKey(Base64.getEncoder().encodeToString(other)));
        assertThrows(JwtException.class, () -> decoder.decode(token("warehouse-slotting", Instant.now().plusSeconds(1800), signer)));
    }
    @Test void malformedRejected() { assertThrows(JwtException.class, () -> decoder.decode("invalid_token")); }
    @Test void shortKeyRejected() { assertThrows(IllegalArgumentException.class, () -> config.jwtSecretKey("AA==")); }
    @Test void invalidLifetimeRejected() { assertThrows(IllegalArgumentException.class, () -> new JwtService(encoder, 0)); }
}
