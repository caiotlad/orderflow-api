package dev.caioladeira.orderflow;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresSupport {
    private static PostgreSQLContainer<?> container;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String external = System.getenv("TEST_DB_URL");
        if (external != null && !external.isBlank()) {
            registry.add("spring.datasource.url", () -> external);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "orderflow"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "test-password"));
        } else {
            if (container == null) {
                container = new PostgreSQLContainer<>("postgres:17-alpine");
                container.start();
            }
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        registry.add("orderflow.security.secret", () -> "test-signing-secret-with-more-than-32-bytes");
        registry.add("orderflow.bootstrap.username", () -> "admin");
        registry.add("orderflow.bootstrap.password", () -> "test-admin-password");
    }
}
