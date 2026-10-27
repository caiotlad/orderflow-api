package dev.caioladeira.orderflow.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserView(UUID id, String username, UserRole role, OffsetDateTime createdAt) {}
