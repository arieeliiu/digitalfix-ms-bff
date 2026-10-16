package cl.digitalfix.bff.dto.request;

import java.math.BigDecimal;

public record ServicioCatalogoRequest(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal tarifa
) {}
