package dev.caioladeira.orderflow.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("orderflow.security")
public record SecurityProperties(@NotBlank String secret, @NotBlank String issuer,
        @Min(60) @Max(86400) long ttlSeconds) {
    public SecurityProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("ORDERFLOW_JWT_SECRET deve ter ao menos 32 bytes UTF-8.");
        }
    }
}
