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
}
