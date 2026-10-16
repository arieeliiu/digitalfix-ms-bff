package cl.digitalfix.bff.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.digitalfix.bff.dto.response.ServicioCatalogoResponse;
import cl.digitalfix.bff.dto.response.RepuestoCatalogoResponse;
import cl.digitalfix.bff.dto.request.ServicioCatalogoRequest;
import cl.digitalfix.bff.dto.request.RepuestoCatalogoRequest;

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

    public ServicioCatalogoResponse crearServicio(ServicioCatalogoRequest solicitud, Jwt jwt) {
        return cliente.post().uri("/api/catalog/services")
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .body(solicitud).retrieve().body(ServicioCatalogoResponse.class);
    }

    public ServicioCatalogoResponse actualizarServicio(Long id, ServicioCatalogoRequest solicitud, Jwt jwt) {
        return cliente.put().uri("/api/catalog/services/{id}", id)
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .body(solicitud).retrieve().body(ServicioCatalogoResponse.class);
    }

    public void eliminarServicio(Long id, Jwt jwt) {
        cliente.delete().uri("/api/catalog/services/{id}", id)
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve().toBodilessEntity();
    }

    public RepuestoCatalogoResponse crearRepuesto(RepuestoCatalogoRequest solicitud, Jwt jwt) {
        return cliente.post().uri("/api/catalog/spare-parts")
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .body(solicitud).retrieve().body(RepuestoCatalogoResponse.class);
    }

    public RepuestoCatalogoResponse actualizarRepuesto(Long id, RepuestoCatalogoRequest solicitud, Jwt jwt) {
        return cliente.put().uri("/api/catalog/spare-parts/{id}", id)
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .body(solicitud).retrieve().body(RepuestoCatalogoResponse.class);
    }

    public void eliminarRepuesto(Long id, Jwt jwt) {
        cliente.delete().uri("/api/catalog/spare-parts/{id}", id)
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve().toBodilessEntity();
    }
}
