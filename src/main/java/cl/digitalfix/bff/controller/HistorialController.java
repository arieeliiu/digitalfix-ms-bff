package cl.digitalfix.bff.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import cl.digitalfix.bff.client.AuditClient;
import cl.digitalfix.bff.dto.response.EventoHistorialResponse;

@RestController
public class HistorialController {
    private final AuditClient audit;

    public HistorialController(AuditClient audit) {
        this.audit = audit;
    }

    @GetMapping("/api/workorders/{id}/history")
    public List<EventoHistorialResponse> historial(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return audit.historial(id, jwt);
    }
}
