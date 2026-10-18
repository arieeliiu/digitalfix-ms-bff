package cl.digitalfix.bff.dto.request;

import java.util.List;

public record CambioEstadoRequest(
        String status,
        String tecnicoId,
        List<RepuestoRequest> repuestos,
        @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=254) String emailTecnico
) {
    public CambioEstadoRequest(String status, String tecnicoId, List<RepuestoRequest> repuestos) {
        this(status, tecnicoId, repuestos, null);
    }
}
