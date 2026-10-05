package cl.digitalfix.bff.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import cl.digitalfix.bff.dto.request.CambioEstadoRequest;
import cl.digitalfix.bff.dto.request.CrearOrdenDominioRequest;
import cl.digitalfix.bff.dto.request.NuevaOrdenRequest;
import cl.digitalfix.bff.dto.response.OrdenResponse;

@Component
public class ClienteOrdenes {
    private final RestClient cliente;

    public ClienteOrdenes(@Qualifier("workordersRestClient") RestClient cliente) {
        this.cliente = cliente;
    }

    public List<OrdenResponse> listar(String solicitanteId, Jwt jwt) {
        var request = cliente.get();
        var uri = solicitanteId == null
                ? request.uri("/api/workorders")
                : request.uri(builder -> builder.path("/api/workorders")
                        .queryParam("solicitanteId", solicitanteId).build());
        return uri.headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve().body(new ParameterizedTypeReference<List<OrdenResponse>>() {});
    }

    public OrdenResponse consultar(Long id, Jwt jwt) {
        return cliente.get().uri("/api/workorders/{id}", id)
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve().body(OrdenResponse.class);
    }

    public OrdenResponse crear(CrearOrdenDominioRequest solicitud, Jwt jwt) {
        return cliente.post().uri("/api/workorders")
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .body(solicitud).retrieve().body(OrdenResponse.class);
    }

    public OrdenResponse actualizar(Long id, String actor, NuevaOrdenRequest solicitud, Jwt jwt) {
        return cliente.put().uri(uri -> uri.path("/api/workorders/{id}")
                        .queryParam("solicitanteId", actor).build(id))
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .body(solicitud).retrieve().body(OrdenResponse.class);
    }

    public OrdenResponse cambiarEstado(Long id, String actor, CambioEstadoRequest solicitud, Jwt jwt) {
        return cliente.put().uri(uri -> uri.path("/api/workorders/{id}/status")
                        .queryParam("solicitanteId", actor).build(id))
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .body(solicitud).retrieve().body(OrdenResponse.class);
    }

    public void eliminar(Long id, String actor, Jwt jwt) {
        cliente.delete().uri(uri -> uri.path("/api/workorders/{id}")
                        .queryParam("solicitanteId", actor).build(id))
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve().toBodilessEntity();
    }
}
