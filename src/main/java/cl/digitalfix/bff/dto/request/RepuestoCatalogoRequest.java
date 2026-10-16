package cl.digitalfix.bff.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RepuestoCatalogoRequest {
    private Long id;
    private String nombre;
    private String descripcion;
    // Conserva el valor por defecto de Catalog cuando el campo no se envía.
    private Integer stock = 0;
}
