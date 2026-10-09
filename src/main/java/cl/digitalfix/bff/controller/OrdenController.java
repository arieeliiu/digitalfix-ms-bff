package cl.digitalfix.bff.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

import cl.digitalfix.bff.dto.request.CambioEstadoRequest;
import cl.digitalfix.bff.dto.request.NuevaOrdenRequest;
import cl.digitalfix.bff.dto.response.OrdenResponse;
import cl.digitalfix.bff.service.OrdenService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrdenController {
    private final OrdenService servicios;

    @GetMapping("/workorders")
    public List<OrdenResponse> ordenes(@AuthenticationPrincipal Jwt jwt) {
        return servicios.listarOrdenes(jwt);
    }

    @GetMapping("/workorders/{id}")
    public OrdenResponse orden(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return servicios.consultarOrden(id, jwt);
    }

    @PostMapping("/workorders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenResponse crear(@RequestBody NuevaOrdenRequest solicitud, @AuthenticationPrincipal Jwt jwt) {
        return servicios.crearOrden(solicitud, jwt);
    }

    @PutMapping("/workorders/{id}")
    public OrdenResponse actualizar(@PathVariable Long id, @RequestBody NuevaOrdenRequest solicitud,
            @AuthenticationPrincipal Jwt jwt) {
        return servicios.actualizarOrden(id, solicitud, jwt);
    }

    @PutMapping("/workorders/{id}/status")
    public OrdenResponse cambiarEstado(@PathVariable Long id, @RequestBody CambioEstadoRequest solicitud,
            @AuthenticationPrincipal Jwt jwt) {
        return servicios.cambiarEstado(id, solicitud, jwt);
    }

    @DeleteMapping("/workorders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        servicios.eliminarOrden(id, jwt);
    }
}
