package dev.caioladeira.orderflow.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserStore {
    private final JdbcTemplate jdbc;

    public UserStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record StoredUser(UUID id, String username, String passwordHash, UserRole role, OffsetDateTime createdAt) {
        public UserView view() { return new UserView(id, username, role, createdAt); }
    }

    public Optional<StoredUser> findByUsername(String username) {
        return jdbc.query("SELECT * FROM app_users WHERE username = ?", this::map,
                username.strip().toLowerCase(Locale.ROOT)).stream().findFirst();
    }

    public Optional<StoredUser> findById(UUID id) {
        return jdbc.query("SELECT * FROM app_users WHERE id = ?", this::map, id).stream().findFirst();
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Long.class);
    }

    public void lockBootstrap() {
        jdbc.query("SELECT pg_advisory_xact_lock(3107202401)", rs -> {});
    }

    public UserView insert(String username, String hash, UserRole role) {
        UUID id = UUID.randomUUID();
        return jdbc.queryForObject("""
                INSERT INTO app_users (id, username, password_hash, role) VALUES (?, ?, ?, ?)
                RETURNING *
                """, this::map, id, username.strip().toLowerCase(Locale.ROOT), hash, role.name()).view();
    }

    private StoredUser map(ResultSet rs, int row) throws SQLException {
        return new StoredUser(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getString("password_hash"), UserRole.valueOf(rs.getString("role")),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
