package dev.caioladeira.orderflow.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserStore users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final String dummyHash;

    public AuthService(UserStore users, PasswordEncoder passwords, JwtEncoder encoder, SecurityProperties properties) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.properties = properties;
        this.dummyHash = passwords.encode("dummy-login-not-a-user-password");
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

    public TokenResponse login(String username, String password) {
        var found = users.findByUsername(username);
        boolean matches = passwords.matches(password, found.map(UserStore.StoredUser::passwordHash).orElse(dummyHash));
        if (found.isEmpty() || !matches) {
            throw new BadCredentialsException("Usuário ou senha inválidos.");
        }
        var user = found.get();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).subject(user.id().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(properties.ttlSeconds()))
                .claim("roles", List.of(user.role().name())).claim("username", user.username()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.ttlSeconds());
    }
}
