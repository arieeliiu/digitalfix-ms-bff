package cl.digitalfix.bff.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import cl.digitalfix.bff.dto.response.EventoHistorialResponse;

@Component
public class AuditClient {
    private final RestClient cliente;

    public AuditClient(@Qualifier("auditRestClient") RestClient cliente) {
        this.cliente = cliente;
    }

    public List<EventoHistorialResponse> historial(Long id, Jwt jwt) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id de orden inválido");
        try {
            // El mismo Bearer se valida también en Audit: no usamos credenciales privilegiadas del BFF.
            var eventos = cliente.get().uri("/api/audit/ordenes/{id}/timeline", id)
                    .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                    .retrieve().body(new ParameterizedTypeReference<List<EventoHistorialResponse>>() {});
            if (eventos == null) throw new RestClientException("Timeline sin cuerpo");
            return eventos;
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            if (status == 401 || status == 403)
                throw new ResponseStatusException(error.getStatusCode(), "Audit rechazó el acceso al historial");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No fue posible consultar el historial");
        } catch (ResourceAccessException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Historial temporalmente no disponible");
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respuesta inesperada de Audit");
        }
    }
}
