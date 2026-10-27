package dev.caioladeira.orderflow.auth;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final UserStore users;
    private final UserService service;
    private final String username;
    private final String password;

    public BootstrapAdmin(UserStore users, UserService service,
            @Value("${orderflow.bootstrap.username}") String username,
            @Value("${orderflow.bootstrap.password}") String password) {
        this.users = users;
        this.service = service;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        users.lockBootstrap();
        if (users.count() != 0) return;
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]{3,60}")) {
            throw new IllegalStateException("Configure ORDERFLOW_BOOTSTRAP_USERNAME com 3 a 60 caracteres: letras, números, ponto, traço ou sublinhado.");
        }
        service.create(normalized, password, UserRole.ADMIN);
    }
}
