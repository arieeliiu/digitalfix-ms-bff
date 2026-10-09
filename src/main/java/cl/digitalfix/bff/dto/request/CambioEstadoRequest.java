package cl.digitalfix.bff.dto.request;

import java.util.List;

public record CambioEstadoRequest(
        String status,
        String tecnicoId,
        List<RepuestoRequest> repuestos
) {}
