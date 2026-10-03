package cl.digitalfix.bff.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;

@Configuration
public class ConversorJwt {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Convierte los scopes del token en permisos con prefijo SCOPE_.
        var conversorScopes = new JwtGrantedAuthoritiesConverter();
        // Convierte los roles de Entra en permisos con prefijo ROLE_.
        var conversorRoles = new JwtGrantedAuthoritiesConverter();

        conversorRoles.setAuthoritiesClaimName("roles");
        conversorRoles.setAuthorityPrefix("ROLE_");

        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
            var autoridades = new ArrayList<GrantedAuthority>();
            autoridades.addAll(conversorScopes.convert(jwt));
            autoridades.addAll(conversorRoles.convert(jwt));
            return autoridades;
        });

        return conversor;
    }
}
