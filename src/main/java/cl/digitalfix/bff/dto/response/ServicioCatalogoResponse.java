package cl.digitalfix.bff.dto.response;

import java.math.BigDecimal;

public record ServicioCatalogoResponse(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal tarifa
) {}
