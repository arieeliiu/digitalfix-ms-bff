package cl.digitalfix.bff.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ControladorAcceso {

    // Requiere un token válido con el scope access_as_user.
    @GetMapping("/perfil")
    public Map<String, String> consultarPerfil() {
        return Map.of("mensaje", "Acceso autenticado a DigitalFix.");
    }

    // Requiere además el rol Admin, según ConfiguracionSeguridad.
    @GetMapping("/administracion")
    public Map<String, String> consultarAdministracion() {
        return Map.of("mensaje", "Acceso de administrador a DigitalFix.");
    }
}