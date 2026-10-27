package dev.caioladeira.orderflow.shared;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityProblemWriter {
    private final ObjectMapper mapper;
    public SecurityProblemWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void unauthorized(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Informe um token de acesso válido.");
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Seu perfil não permite esta operação.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(),
                ApiExceptionHandler.problem(status, code, message, request.getRequestURI()));
    }
}
