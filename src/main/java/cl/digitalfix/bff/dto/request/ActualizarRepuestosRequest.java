package cl.digitalfix.bff.dto.request;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ActualizarRepuestosRequest(
        @NotNull List<@Valid RepuestoRequest> anteriores,
        @NotNull List<@Valid RepuestoRequest> nuevos
) {}
