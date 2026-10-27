package dev.caioladeira.orderflow.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,60}") String username,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotNull UserRole role) {}

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastrar administrador ou operador")
    public ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        UserView user = users.create(request.username(), request.password(), request.role());
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }
}
