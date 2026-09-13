package cl.digitalfix.bff;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SeguridadBffTests {

    private static final RSAKey CLAVE = generarClave();
    private static final RSAKey OTRA_CLAVE = generarClave();
    private static final HttpServer SERVIDOR = iniciarServidor();

    @Autowired
    private MockMvc cliente;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String emisor;

    @Value("${spring.security.oauth2.resourceserver.jwt.audiences}")
    private String audiencia;

    // Solo sustituimos el origen de las claves públicas durante las pruebas.
    // Conservamos la configuración real de emisor, audiencia y autorización.
    @DynamicPropertySource
    static void configurarClaves(DynamicPropertyRegistry propiedades) {
        propiedades.add(
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://127.0.0.1:" + SERVIDOR.getAddress().getPort() + "/claves"
        );
    }

    @ParameterizedTest(name = "{0} en {1} debe responder {2}")
    @CsvSource({
        "sin_token,       /api/perfil,          401",
        "malformado,      /api/perfil,          401",
        "firma_invalida,  /api/perfil,          401",
        "emisor_invalido, /api/perfil,          401",
        "audiencia_invalida, /api/perfil,       401",
        "vencido,         /api/perfil,          401",
        "futuro,          /api/perfil,          401",
        "sin_scope,       /api/perfil,          403",
        "sin_scope,       /api/administracion,  403",
        "sin_rol,         /api/administracion,  403",
        "Operador,        /api/administracion,  403",
        "Cliente,         /api/administracion,  403",
        "Operador,        /api/perfil,          200",
        "Cliente,         /api/perfil,          200",
        "Admin,           /api/perfil,          200",
        "Admin,           /api/administracion,  200"
    })
    void comprobarAcceso(String caso, String ruta, int codigoEsperado)
            throws Exception {
        var solicitud = get(ruta);

        if (!caso.equals("sin_token")) {
            String token = caso.equals("malformado")
                ? "token-invalido"
                : crearToken(caso);
            solicitud.header("Authorization", "Bearer " + token);
        }

        cliente.perform(solicitud)
            .andExpect(status().is(codigoEsperado));
    }

    private String crearToken(String caso) throws Exception {
        Instant ahora = Instant.now();

        var claims = new JWTClaimsSet.Builder()
            .subject("usuario-prueba")
            .issuer(caso.equals("emisor_invalido")
                ? "https://emisor-incorrecto.example" : emisor)
            .audience(caso.equals("audiencia_invalida")
                ? "otra-api" : audiencia)
            .issueTime(Date.from(ahora.minusSeconds(1200)))
            .notBeforeTime(Date.from(caso.equals("futuro")
                ? ahora.plusSeconds(600) : ahora.minusSeconds(1200)))
            .expirationTime(Date.from(caso.equals("vencido")
                ? ahora.minusSeconds(600) : ahora.plusSeconds(1200)));

        if (!caso.equals("sin_scope")) {
            claims.claim("scp", "access_as_user");
        }

        if (!caso.equals("sin_rol")) {
            String rol = switch (caso) {
                case "Operador" -> "Operador";
                case "Cliente" -> "Cliente";
                default -> "Admin";
            };
            claims.claim("roles", List.of(rol));
        }

        var token = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(CLAVE.getKeyID())
                .build(),
            claims.build()
        );

        // La firma incorrecta usa una clave distinta de la publicada.
        token.sign(new RSASSASigner(
            caso.equals("firma_invalida") ? OTRA_CLAVE : CLAVE
        ));
        return token.serialize();
    }

    private static RSAKey generarClave() {
        try {
            return new RSAKeyGenerator(2048)
                .keyID("clave-prueba")
                .generate();
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo generar la clave", error);
        }
    }

    private static HttpServer iniciarServidor() {
        try {
            var servidor = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
            );
            byte[] respuesta = new JWKSet(CLAVE.toPublicJWK())
                .toString().getBytes(StandardCharsets.UTF_8);

            servidor.createContext("/claves", intercambio -> {
                intercambio.getResponseHeaders()
                    .set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, respuesta.length);
                try (var salida = intercambio.getResponseBody()) {
                    salida.write(respuesta);
                }
            });
            servidor.start();
            return servidor;
        } catch (Exception error) {
            throw new IllegalStateException(
                "No se pudo iniciar el servidor de claves de prueba", error
            );
        }
    }

    @AfterAll
    static void detenerServidor() {
        SERVIDOR.stop(0);
    }
}