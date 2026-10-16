package cl.digitalfix.bff.dto.response;

public record RepuestoCatalogoResponse(
        Long id,
        String nombre,
        String descripcion,
        Integer stock
) {}
