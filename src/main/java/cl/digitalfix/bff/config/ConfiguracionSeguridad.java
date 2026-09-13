package cl.digitalfix.bff.config;

import java.util.ArrayList;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.authorization.AuthorizationManagers.allOf;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole;

@Configuration
public class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain configurarSeguridad(HttpSecurity http) throws Exception {
        http
            // La API recibe tokens Bearer y no utiliza sesiones ni cookies de autenticación.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sesion ->
                sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(solicitudes -> solicitudes
                // Administración requiere tanto el scope como el rol Admin.
                .requestMatchers("/api/administracion", "/api/administracion/**")
                    .access(allOf(
                        hasAuthority("SCOPE_access_as_user"),
                        hasRole("Admin")
                    ))
                // Las demás rutas requieren el permiso delegado de nuestra API.
                .anyRequest().hasAuthority("SCOPE_access_as_user")
            )
            .oauth2ResourceServer(recurso -> recurso
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(convertirAutorizaciones())
                )
            );

        return http.build();
    }

    private JwtAuthenticationConverter convertirAutorizaciones() {
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