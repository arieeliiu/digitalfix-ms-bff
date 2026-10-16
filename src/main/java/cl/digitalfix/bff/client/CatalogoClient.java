package cl.digitalfix.bff.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.digitalfix.bff.dto.response.ServicioCatalogoResponse;
import cl.digitalfix.bff.dto.response.RepuestoCatalogoResponse;

@Component
public class CatalogoClient {
    private final RestClient cliente;

    public CatalogoClient(@Qualifier("catalogRestClient") RestClient cliente) {
        this.cliente = cliente;
    }

    public List<ServicioCatalogoResponse> listar(Jwt jwt) {
        return cliente.get().uri("/api/catalog/services")
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve().body(new ParameterizedTypeReference<List<ServicioCatalogoResponse>>() {});
    }

    public List<RepuestoCatalogoResponse> listarRepuestos(Jwt jwt) {
        return cliente.get().uri("/api/catalog/spare-parts")
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve().body(new ParameterizedTypeReference<List<RepuestoCatalogoResponse>>() {});
    }
}
