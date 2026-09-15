package cl.digitalfix.bff.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import cl.digitalfix.bff.service.ServiciosDominio;
import cl.digitalfix.bff.service.ServiciosDominio.*;

@RestController
@RequestMapping("/api")
public class ControladorNegocio {
    private final ServiciosDominio servicios;
    public ControladorNegocio(ServiciosDominio servicios) { this.servicios = servicios; }

    @GetMapping("/catalog/services")
    public List<ServicioCatalogo> catalogo(@AuthenticationPrincipal Jwt jwt) {
        return servicios.listarServicios(jwt);
    }

    @GetMapping("/workorders")
    public List<Orden> ordenes(@AuthenticationPrincipal Jwt jwt) {
        return servicios.listarOrdenes(jwt);
    }

    @GetMapping("/workorders/{id}")
    public Orden orden(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return servicios.consultarOrden(id, jwt);
    }

    @PostMapping("/workorders")
    @ResponseStatus(HttpStatus.CREATED)
    public Orden crear(@RequestBody NuevaOrden solicitud, @AuthenticationPrincipal Jwt jwt) {
        return servicios.crearOrden(solicitud, jwt);
    }

    @PutMapping("/workorders/{id}")
    public Orden actualizar(@PathVariable Long id, @RequestBody NuevaOrden solicitud,
            @AuthenticationPrincipal Jwt jwt) {
        return servicios.actualizarOrden(id, solicitud, jwt);
    }

    @PutMapping("/workorders/{id}/status")
    public Orden cambiarEstado(@PathVariable Long id, @RequestBody CambioEstado solicitud,
            @AuthenticationPrincipal Jwt jwt) {
        return servicios.cambiarEstado(id, solicitud, jwt);
    }

    @DeleteMapping("/workorders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        servicios.eliminarOrden(id, jwt);
    }
}
