package cl.digitalfix.bff.config.security;

import java.util.ArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class ConversorJwt {
    @Bean
    JwtAuthenticationConverter convertirAutorizaciones() {
        // Convierte los scopes del token en permisos con prefijo SCOPE_.
        var conversorScopes = new JwtGrantedAuthoritiesConverter();

        // Convierte los roles de Entra en permisos con prefijo ROLE_.
        var conversorRoles = new JwtGrantedAuthoritiesConverter();
        conversorRoles.setAuthoritiesClaimName("roles");
        conversorRoles.setAuthorityPrefix("ROLE_");

        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
            var autorizaciones = new ArrayList<GrantedAuthority>();
            autorizaciones.addAll(conversorScopes.convert(jwt));
            autorizaciones.addAll(conversorRoles.convert(jwt));
            return autorizaciones;
        });

        return conversor;
    }
}
