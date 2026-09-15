package cl.digitalfix.bff.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ErroresDominio {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail negocio(ResponseStatusException error) {
        return ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason() == null ? "Solicitud rechazada" : error.getReason());
    }

    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail respuesta(RestClientResponseException error) {
        var status = error.getStatusCode().value();
        if (status == 400 || status == 404 || status == 409) {
            return ProblemDetail.forStatusAndDetail(error.getStatusCode(), "El servicio rechazó la operación solicitada");
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "No se pudo completar la operación en el servicio");
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail conexion(ResourceAccessException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible; consulta tus órdenes antes de volver a enviar una creación");
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail formato(RestClientException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Respuesta inesperada del servicio");
    }
}
