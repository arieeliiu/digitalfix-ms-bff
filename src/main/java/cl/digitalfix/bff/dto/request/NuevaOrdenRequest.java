package cl.digitalfix.bff.dto.request;

public record NuevaOrdenRequest(
        Long servicioId,
        String descripcion,
        String direccion
) {}
