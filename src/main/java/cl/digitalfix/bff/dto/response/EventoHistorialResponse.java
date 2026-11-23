package cl.digitalfix.bff.dto.response;

import java.time.Instant;

// Contrato de lectura de Audit; no expone detalles personales de la orden.
public record EventoHistorialResponse(
        String eventId, String tipo, Long ordenId, String accion,
        String estadoAnterior, String estadoNuevo, String tecnicoId,
        String actorId, String actorRoles, Instant fecha, Instant recibidoEn,
        String correlationId, Integer kafkaParticion, Long kafkaOffset, String kafkaTopico) {}
