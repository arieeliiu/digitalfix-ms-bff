package cl.digitalfix.bff.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import cl.digitalfix.bff.dto.response.ServicioCatalogoResponse;

@Component
public class ClienteCatalogo {
    private final RestClient cliente;

    public ClienteCatalogo(@Qualifier("catalogRestClient") RestClient cliente) {
        this.cliente = cliente;
    }

    public List<ServicioCatalogoResponse> listar(Jwt jwt) {
        return cliente.get().uri("/api/catalog/services")
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve().body(new ParameterizedTypeReference<List<ServicioCatalogoResponse>>() {});
    }
}
