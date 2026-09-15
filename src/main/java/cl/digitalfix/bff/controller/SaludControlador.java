package cl.digitalfix.bff.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaludControlador {
    // Sonda del balanceador. No consulta Oracle ni revela configuración.
    @GetMapping("/healthz")
    public Map<String, String> salud() { return Map.of("estado", "ok"); }
}
