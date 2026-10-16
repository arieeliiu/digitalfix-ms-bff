package cl.digitalfix.bff.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

import cl.digitalfix.bff.client.CatalogoClient;
import cl.digitalfix.bff.dto.response.ServicioCatalogoResponse;
import cl.digitalfix.bff.dto.response.RepuestoCatalogoResponse;
import cl.digitalfix.bff.dto.request.ServicioCatalogoRequest;
import cl.digitalfix.bff.dto.request.RepuestoCatalogoRequest;

@Service
@RequiredArgsConstructor
public class CatalogoService {
    private final CatalogoClient cliente;

    public List<ServicioCatalogoResponse> listarServicios(Jwt jwt) {
        var resultado = cliente.listar(jwt);
        if (resultado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogo sin respuesta");
        }
        return resultado;
    }

    public List<RepuestoCatalogoResponse> listarRepuestos(Jwt jwt) {
        var resultado = cliente.listarRepuestos(jwt);
        if (resultado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogo sin respuesta");
        }
        return resultado;
    }

    public ServicioCatalogoResponse crearServicio(ServicioCatalogoRequest solicitud, Jwt jwt) {
        var resultado = cliente.crearServicio(solicitud, jwt);
        if (resultado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogo sin respuesta");
        }
        return resultado;
    }

    public ServicioCatalogoResponse actualizarServicio(Long id, ServicioCatalogoRequest solicitud, Jwt jwt) {
        var resultado = cliente.actualizarServicio(id, solicitud, jwt);
        if (resultado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogo sin respuesta");
        }
        return resultado;
    }

    public void eliminarServicio(Long id, Jwt jwt) {
        cliente.eliminarServicio(id, jwt);
    }

    public RepuestoCatalogoResponse crearRepuesto(RepuestoCatalogoRequest solicitud, Jwt jwt) {
        var resultado = cliente.crearRepuesto(solicitud, jwt);
        if (resultado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogo sin respuesta");
        }
        return resultado;
    }

    public RepuestoCatalogoResponse actualizarRepuesto(Long id, RepuestoCatalogoRequest solicitud, Jwt jwt) {
        var resultado = cliente.actualizarRepuesto(id, solicitud, jwt);
        if (resultado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogo sin respuesta");
        }
        return resultado;
    }

    public void eliminarRepuesto(Long id, Jwt jwt) {
        cliente.eliminarRepuesto(id, jwt);
    }
}
