package dev.caioladeira.orderflow.auth;

import dev.caioladeira.orderflow.shared.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final UserStore users;

    public AuthController(AuthService auth, UserStore users) { this.auth = auth; this.users = users; }

    public record LoginRequest(
            @NotBlank @Size(max = 60) String username,
            @NotBlank @Size(max = 72) String password) {}

    @PostMapping("/token")
    @Operation(summary = "Obter token de acesso", security = {})
    public AuthService.TokenResponse token(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Consultar usuário autenticado")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> BusinessException.missing("Usuário")).view();
    }
}
