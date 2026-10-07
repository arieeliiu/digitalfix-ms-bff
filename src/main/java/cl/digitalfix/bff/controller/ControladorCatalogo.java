package cl.digitalfix.bff.controller;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import cl.digitalfix.bff.service.ServicioCatalogo;
import cl.digitalfix.bff.dto.response.ServicioCatalogoResponse;

@RestController
@RequestMapping("/api/catalog")
public class ControladorCatalogo {
    private final ServicioCatalogo servicio;

    public ControladorCatalogo(ServicioCatalogo servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/services")
    public List<ServicioCatalogoResponse> catalogo(@AuthenticationPrincipal Jwt jwt) {
        return servicio.listarServicios(jwt);
    }
}
