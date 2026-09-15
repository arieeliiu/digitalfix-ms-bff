package cl.digitalfix.bff.service;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServiciosDominio {
    public record ServicioCatalogo(Long id, String nombre, String descripcion, BigDecimal tarifa) {}
    public record NuevaOrden(Long servicioId, String descripcion, String direccion) {}
    public record CambioEstado(String status, String tecnicoId) {}
    public record Orden(Long id, Long servicioId, String descripcion, String direccion,
                        String solicitanteId, Instant fechaCreacion, String estado,
                        String tecnicoId, String actualizadoPor, Instant fechaActualizacion) {}
    private record OrdenInterna(Long servicioId, String descripcion, String direccion, String solicitanteId) {}

    private final RestClient catalog;
    private final RestClient workorders;

    public ServiciosDominio(@Value("${digitalfix.catalog-url}") String catalogUrl,
                            @Value("${digitalfix.workorders-url}") String workordersUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        catalog = RestClient.builder().baseUrl(catalogUrl).requestFactory(factory).build();
        workorders = RestClient.builder().baseUrl(workordersUrl).requestFactory(factory).build();
    }

    public List<ServicioCatalogo> listarServicios(Jwt jwt) {
        var resultado = catalog.get().uri("/api/catalog/services")
            .headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve()
            .body(new ParameterizedTypeReference<List<ServicioCatalogo>>() {});
        if (resultado == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catálogo sin respuesta");
        return resultado;
    }

    public List<Orden> listarOrdenes(Jwt jwt) {
        String solicitante = identidad(jwt);
        var resultado = workorders.get()
            .uri(uri -> uri.path("/api/workorders").queryParam("solicitanteId", solicitante).build())
            .headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve()
            .body(new ParameterizedTypeReference<List<Orden>>() {});
        if (resultado == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Órdenes sin respuesta");
        // Protección adicional si todavía se está ejecutando una versión antigua del microservicio.
        return resultado.stream().filter(o -> solicitante.equals(o.solicitanteId())).toList();
    }

    public Orden consultarOrden(Long id, Jwt jwt) {
        var orden = workorders.get().uri("/api/workorders/{id}", id)
            .headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Orden.class);
        if (orden == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Orden sin respuesta");
        if (!identidad(jwt).equals(orden.solicitanteId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada");
        }
        return orden;
    }

    public Orden crearOrden(NuevaOrden solicitud, Jwt jwt) {
        String solicitante = identidad(jwt);
        validarSolicitud(solicitud, jwt);
        var interna = new OrdenInterna(solicitud.servicioId(), solicitud.descripcion().trim(),
            solicitud.direccion().trim(), solicitante);
        var orden = workorders.post().uri("/api/workorders")
            .headers(h -> h.setBearerAuth(jwt.getTokenValue())).body(interna).retrieve().body(Orden.class);
        if (orden == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Orden sin respuesta");
        return orden;
    }

    public Orden actualizarOrden(Long id, NuevaOrden solicitud, Jwt jwt) {
        consultarOrden(id, jwt);
        validarSolicitud(solicitud, jwt);
        var orden = workorders.put()
            .uri(uri -> uri.path("/api/workorders/{id}").queryParam("solicitanteId", identidad(jwt)).build(id))
            .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
            .body(new NuevaOrden(solicitud.servicioId(), solicitud.descripcion().trim(), solicitud.direccion().trim()))
            .retrieve().body(Orden.class);
        if (orden == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Orden sin respuesta");
        return orden;
    }

    public Orden cambiarEstado(Long id, CambioEstado solicitud, Jwt jwt) {
        consultarOrden(id, jwt);
        if (solicitud.status() == null || !List.of("CREADA", "ASIGNADA", "EN_DESPLAZAMIENTO",
                "EN_EJECUCION", "CERRADA", "CANCELADA").contains(solicitud.status())
                || (solicitud.tecnicoId() != null && solicitud.tecnicoId().length() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado o técnico inválido");
        }
        var orden = workorders.put()
            .uri(uri -> uri.path("/api/workorders/{id}/status").queryParam("solicitanteId", identidad(jwt)).build(id))
            .headers(h -> h.setBearerAuth(jwt.getTokenValue())).body(solicitud)
            .retrieve().body(Orden.class);
        if (orden == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Orden sin respuesta");
        return orden;
    }

    public void eliminarOrden(Long id, Jwt jwt) {
        consultarOrden(id, jwt);
        workorders.delete()
            .uri(uri -> uri.path("/api/workorders/{id}").queryParam("solicitanteId", identidad(jwt)).build(id))
            .headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().toBodilessEntity();
    }

    private void validarSolicitud(NuevaOrden solicitud, Jwt jwt) {
        if (solicitud.servicioId() == null || solicitud.servicioId() <= 0
            || solicitud.descripcion() == null || solicitud.descripcion().isBlank() || solicitud.descripcion().length() > 1000
            || solicitud.direccion() == null || solicitud.direccion().isBlank() || solicitud.direccion().length() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Servicio, descripción y dirección válidos son obligatorios");
        }
        if (listarServicios(jwt).stream().noneMatch(s -> solicitud.servicioId().equals(s.id()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El servicio seleccionado no existe");
        }
    }

    private String identidad(Jwt jwt) {
        // oid identifica al usuario en el tenant validado. Sin fallback a sub: evita dos identidades por usuario.
        String subject = jwt.getClaimAsString("oid");
        if (subject == null || subject.isBlank() || subject.length() > 100) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El token no identifica un solicitante válido");
        }
        return subject;
    }
}
