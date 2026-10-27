package dev.caioladeira.orderflow.auth;

import dev.caioladeira.orderflow.shared.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserStore users;
    private final PasswordEncoder passwords;

    public UserService(UserStore users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public UserView create(String username, String password, UserRole role) {
        validatePassword(password);
        try {
            return users.insert(username, passwords.encode(password), role);
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("USERNAME_EXISTS", "Este usuário já está cadastrado.");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw BusinessException.invalid("INVALID_PASSWORD", "A senha deve ter ao menos 12 caracteres e no máximo 72 bytes UTF-8.");
        }
    }
}
