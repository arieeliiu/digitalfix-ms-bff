package cl.digitalfix.bff.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import cl.digitalfix.bff.client.ClienteCatalogo;
import cl.digitalfix.bff.dto.response.ServicioCatalogoResponse;

@Service
public class ServicioCatalogo {
    private final ClienteCatalogo cliente;

    public ServicioCatalogo(ClienteCatalogo cliente) {
        this.cliente = cliente;
    }

    public List<ServicioCatalogoResponse> listarServicios(Jwt jwt) {
        var resultado = cliente.listar(jwt);
        if (resultado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogo sin respuesta");
        }
        return resultado;
    }
}
