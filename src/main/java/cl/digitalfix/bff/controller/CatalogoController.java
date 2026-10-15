package cl.digitalfix.bff.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

import cl.digitalfix.bff.dto.response.ServicioCatalogoResponse;
import cl.digitalfix.bff.service.CatalogoService;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogoController {
    private final CatalogoService servicio;

    @GetMapping("/services")
    public List<ServicioCatalogoResponse> catalogo(@AuthenticationPrincipal Jwt jwt) {
        return servicio.listarServicios(jwt);
    }
}
