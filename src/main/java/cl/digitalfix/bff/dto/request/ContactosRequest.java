package cl.digitalfix.bff.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// null conserva el dato; cadena vacía permite limpiarlo en Workorders.
public record ContactosRequest(@Email @Size(max=254) String emailContacto,
                               @Email @Size(max=254) String emailTecnico) {}
