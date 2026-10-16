package cl.digitalfix.bff.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

import cl.digitalfix.bff.dto.response.ServicioCatalogoResponse;
import cl.digitalfix.bff.dto.response.RepuestoCatalogoResponse;
import cl.digitalfix.bff.service.CatalogoService;
import cl.digitalfix.bff.dto.request.ServicioCatalogoRequest;
import cl.digitalfix.bff.dto.request.RepuestoCatalogoRequest;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogoController {
    private final CatalogoService servicio;

    @GetMapping("/services")
    public List<ServicioCatalogoResponse> catalogo(@AuthenticationPrincipal Jwt jwt) {
        return servicio.listarServicios(jwt);
    }

    @GetMapping("/spare-parts")
    public List<RepuestoCatalogoResponse> repuestos(@AuthenticationPrincipal Jwt jwt) {
        return servicio.listarRepuestos(jwt);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServicioCatalogoResponse crearServicio(@RequestBody ServicioCatalogoRequest solicitud,
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.crearServicio(solicitud, jwt);
    }

    @PutMapping("/services/{id}")
    public ServicioCatalogoResponse actualizarServicio(@PathVariable Long id,
            @RequestBody ServicioCatalogoRequest solicitud, @AuthenticationPrincipal Jwt jwt) {
        return servicio.actualizarServicio(id, solicitud, jwt);
    }

    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarServicio(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        servicio.eliminarServicio(id, jwt);
    }

    @PostMapping("/spare-parts")
    @ResponseStatus(HttpStatus.CREATED)
    public RepuestoCatalogoResponse crearRepuesto(@RequestBody RepuestoCatalogoRequest solicitud,
            @AuthenticationPrincipal Jwt jwt) {
        return servicio.crearRepuesto(solicitud, jwt);
    }

    @PutMapping("/spare-parts/{id}")
    public RepuestoCatalogoResponse actualizarRepuesto(@PathVariable Long id,
            @RequestBody RepuestoCatalogoRequest solicitud, @AuthenticationPrincipal Jwt jwt) {
        return servicio.actualizarRepuesto(id, solicitud, jwt);
    }

    @DeleteMapping("/spare-parts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarRepuesto(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        servicio.eliminarRepuesto(id, jwt);
    }
}
