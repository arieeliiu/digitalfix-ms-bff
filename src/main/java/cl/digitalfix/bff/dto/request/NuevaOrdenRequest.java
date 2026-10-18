package cl.digitalfix.bff.dto.request;

public record NuevaOrdenRequest(
        Long servicioId,
        String descripcion,
        String direccion,
        @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=254) String emailContacto
) {
    public NuevaOrdenRequest(Long servicioId, String descripcion, String direccion) {
        this(servicioId, descripcion, direccion, null);
    }
}
