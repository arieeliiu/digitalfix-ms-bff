package cl.digitalfix.bff.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

import cl.digitalfix.bff.client.OrdenClient;
import cl.digitalfix.bff.dto.request.CambioEstadoRequest;
import cl.digitalfix.bff.dto.request.ActualizarRepuestosRequest;
import cl.digitalfix.bff.dto.request.ContactosRequest;
import cl.digitalfix.bff.dto.request.CrearOrdenDominioRequest;
import cl.digitalfix.bff.dto.request.NuevaOrdenRequest;
import cl.digitalfix.bff.dto.response.OrdenResponse;

@Service
@RequiredArgsConstructor
public class OrdenService {

    private final OrdenClient ordenes;
    private final CatalogoService catalogo;

    public List<OrdenResponse> listarOrdenes(Jwt jwt) {

        String actor = identidad(jwt);

        if (puedeGestionarOrdenes(jwt)) {

            var resultado = ordenes.listar(null, jwt);

            if (resultado == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Órdenes sin respuesta");
            }

            return resultado;
        }

        var resultado = ordenes.listar(actor, jwt);

        if (resultado == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Órdenes sin respuesta");
        }

        return resultado.stream()
                .filter(o -> actor.equals(o.solicitanteId()))
                .toList();
    }

    public OrdenResponse consultarOrden(Long id, Jwt jwt) {

        var orden = ordenes.consultar(id, jwt);

        if (orden == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Orden sin respuesta");
        }

        if (!puedeGestionarOrdenes(jwt)
                && !identidad(jwt).equals(orden.solicitanteId())) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Orden no encontrada");
        }

        return orden;
    }

    public OrdenResponse crearOrden(NuevaOrdenRequest solicitud, Jwt jwt) {

        String solicitante = identidad(jwt);

        validarSolicitud(solicitud, jwt);

        var interna = new CrearOrdenDominioRequest(
                solicitud.servicioId(),
                solicitud.descripcion().trim(),
                solicitud.direccion().trim(),
                solicitante,
                solicitud.emailContacto());

        var orden = ordenes.crear(interna, jwt);

        if (orden == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Orden sin respuesta");
        }

        return orden;
    }

    public OrdenResponse actualizarOrden(
            Long id,
            NuevaOrdenRequest solicitud,
            Jwt jwt) {

        validarModificacion(id, jwt, List.of("CREADA"));
        validarSolicitud(solicitud, jwt);

        var orden = ordenes.actualizar(id, new NuevaOrdenRequest(
                        solicitud.servicioId(),
                        solicitud.descripcion().trim(),
                        solicitud.direccion().trim()), jwt);

        if (orden == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Orden sin respuesta");
        }

        return orden;
    }

    public OrdenResponse cambiarEstado(
            Long id,
            CambioEstadoRequest solicitud,
            Jwt jwt) {

        var actual = consultarOrden(id, jwt);
        if ("CREADA".equals(actual.estado()) && "ASIGNADA".equals(solicitud.status())
                && (solicitud.emailTecnico() == null || solicitud.emailTecnico().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo del técnico es obligatorio al asignar");

        boolean estadoInvalido =
                solicitud.status() == null
                || !List.of(
                        "CREADA",
                        "ASIGNADA",
                        "EN_DESPLAZAMIENTO",
                        "EN_EJECUCION",
                        "CERRADA",
                        "CANCELADA")
                    .contains(solicitud.status());

        boolean tecnicoInvalido =
                solicitud.tecnicoId() != null
                && solicitud.tecnicoId().length() > 100;

        boolean repuestosInvalidos =
                solicitud.repuestos() != null
                && solicitud.repuestos().stream().anyMatch(repuesto ->
                        repuesto == null
                        || repuesto.repuestoId() == null
                        || repuesto.repuestoId() <= 0
                        || repuesto.cantidad() == null
                        || repuesto.cantidad() <= 0);

        boolean repuestosFueraDeAsignacion =
                !"ASIGNADA".equals(solicitud.status())
                && solicitud.repuestos() != null
                && !solicitud.repuestos().isEmpty();

        if (estadoInvalido
                || tecnicoInvalido
                || repuestosInvalidos
                || repuestosFueraDeAsignacion) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Estado, técnico o repuestos inválidos");
        }

        var orden = ordenes.cambiarEstado(id, identidad(jwt), solicitud, jwt);

        if (orden == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Orden sin respuesta");
        }

        return orden;
    }

    public OrdenResponse actualizarRepuestos(Long id, ActualizarRepuestosRequest solicitud, Jwt jwt) {
        identidad(jwt);
        if (!puedeGestionarOrdenes(jwt))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La gestión de repuestos requiere rol Admin u Operador");
        var orden = consultarOrden(id, jwt);
        if (!List.of("ASIGNADA", "EN_DESPLAZAMIENTO", "EN_EJECUCION").contains(orden.estado()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La orden no admite cambios de repuestos");
        var resultado = ordenes.actualizarRepuestos(id, solicitud, jwt);
        if (resultado == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
        return resultado;
    }

    public void eliminarOrden(Long id, Jwt jwt) {

        validarModificacion(id, jwt, List.of("CREADA", "CANCELADA"));

        ordenes.eliminar(id, jwt);
    }

    private void validarModificacion(Long id, Jwt jwt, List<String> estados) {
        String actor = identidad(jwt);
        var orden = consultarOrden(id, jwt);
        var roles = jwt.getClaimAsStringList("roles");
        if ((roles == null || !roles.contains("Admin")) && !actor.equals(orden.solicitanteId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada");
        if (!estados.contains(orden.estado()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El estado de la orden no permite esta operación");
    }

    public OrdenResponse actualizarContactos(Long id, ContactosRequest solicitud, Jwt jwt) {
        identidad(jwt);
        var actual = consultarOrden(id, jwt);
        if (!puedeGestionarOrdenes(jwt) && solicitud.emailTecnico() != null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El correo del técnico requiere gestión de órdenes");
        if (actual.tecnicoId() != null && solicitud.emailTecnico() != null && solicitud.emailTecnico().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo del técnico asignado no puede borrarse");
        var orden = ordenes.actualizarContactos(id, solicitud, jwt);
        if (orden == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Orden sin respuesta");
        return orden;
    }

    private void validarSolicitud(
            NuevaOrdenRequest solicitud,
            Jwt jwt) {

        if (solicitud.servicioId() == null
                || solicitud.servicioId() <= 0
                || solicitud.descripcion() == null
                || solicitud.descripcion().isBlank()
                || solicitud.descripcion().length() > 1000
                || solicitud.direccion() == null
                || solicitud.direccion().isBlank()
                || solicitud.direccion().length() > 300) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Servicio, descripción y dirección válidos son obligatorios");
        }

        if (catalogo.listarServicios(jwt).stream()
                .noneMatch(s -> solicitud.servicioId().equals(s.id()))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El servicio seleccionado no existe");
        }
    }

    private boolean puedeGestionarOrdenes(Jwt jwt) {

        var roles = jwt.getClaimAsStringList("roles");

        return roles != null
                && (roles.contains("Operador")
                    || roles.contains("Admin"));
    }

    private String identidad(Jwt jwt) {

        String subject = jwt.getClaimAsString("oid");

        if (subject == null
                || subject.isBlank()
                || subject.length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "El token no identifica un solicitante válido");
        }

        return subject;
    }
}
