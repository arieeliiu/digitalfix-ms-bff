package cl.digitalfix.bff.dto.request;

public record CrearOrdenDominioRequest(
        Long servicioId,
        String descripcion,
        String direccion,
        String solicitanteId
) {}
