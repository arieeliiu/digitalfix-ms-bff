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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    private static final AtomicReference<String> TOKEN_INTERNO = new AtomicReference<>();
    private static final AtomicReference<String> CUERPO_INTERNO = new AtomicReference<>();
    private static final AtomicReference<String> FILTRO_INTERNO = new AtomicReference<>();
    private static final AtomicInteger CREACIONES = new AtomicInteger();
    private static final String ORDEN = """
        {"id":1,"servicioId":1,"descripcion":"Revisión","direccion":"Calle 123",
         "solicitanteId":"usuario-prueba","estado":"CREADA","fechaCreacion":"2026-09-15T00:00:00Z"}
        """;

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
        propiedades.add("digitalfix.catalog-url", () -> "http://127.0.0.1:" + SERVIDOR.getAddress().getPort());
        propiedades.add("digitalfix.workorders-url", () -> "http://127.0.0.1:" + SERVIDOR.getAddress().getPort());
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
        ,"sin_token,      /api/workorders,      401"
        ,"firma_invalida, /api/catalog/services,401"
        ,"sin_scope,      /api/workorders,      403"
        ,"sin_rol,        /api/catalog/services,403"
        ,"sin_rol,        /api/perfil,          403"
        ,"sin_oid,        /api/workorders,      403"
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
            .subject("subject-distinto")
            .claim("oid", caso.equals("sin_oid") ? null : "usuario-prueba")
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

    @Test
    void saludYOpcionesNoExigenToken() throws Exception {
        cliente.perform(get("/healthz")).andExpect(status().isOk());
        cliente.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/workorders"))
            .andExpect(status().isOk());
    }

    @Test
    void flujoCatalogoCrearYConsultarConJwtFirmado() throws Exception {
        String token = crearToken("Cliente");
        cliente.perform(get("/api/catalog/services").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].tarifa").value(25000));
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());

        cliente.perform(post("/api/workorders").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"servicioId":1,"descripcion":"Revisión","direccion":"Calle 123","solicitanteId":"otra-persona"}
                """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.solicitanteId").value("usuario-prueba"));
        assertTrue(CUERPO_INTERNO.get().contains("usuario-prueba"));
        assertFalse(CUERPO_INTERNO.get().contains("otra-persona"));
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());

        cliente.perform(get("/api/workorders").param("solicitanteId", "otra-persona")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1));
        assertEquals("solicitanteId=usuario-prueba", FILTRO_INTERNO.get());
        cliente.perform(get("/api/workorders/1").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CREADA"));
    }

    @Test
    void noRevelaOrdenDeOtraPersona() throws Exception {
        cliente.perform(get("/api/workorders/2").header("Authorization", "Bearer " + crearToken("Cliente")))
            .andExpect(status().isNotFound());
    }

    @Test
    void noCreaConServicioInexistente() throws Exception {
        int antes = CREACIONES.get();
        cliente.perform(post("/api/workorders").header("Authorization", "Bearer " + crearToken("Cliente"))
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"servicioId":999,"descripcion":"Revisión","direccion":"Calle 123"}
                """))
            .andExpect(status().isBadRequest());
        assertEquals(antes, CREACIONES.get());
    }

    @Test
    void rechazaCreacionSinScopeAntesDeLlamarServicios() throws Exception {
        int antes = CREACIONES.get();
        cliente.perform(post("/api/workorders").header("Authorization", "Bearer " + crearToken("sin_scope"))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        assertEquals(antes, CREACIONES.get());
    }

    @Test
    void conserva404YTraduceFalloDeServicioA502() throws Exception {
        String token = crearToken("Cliente");
        cliente.perform(get("/api/workorders/404").header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
        cliente.perform(get("/api/workorders/500").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadGateway());
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
            byte[] clavesPublicas = new JWKSet(CLAVE.toPublicJWK())
                .toString().getBytes(StandardCharsets.UTF_8);

            servidor.createContext("/claves", intercambio -> {
                intercambio.getResponseHeaders()
                    .set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, clavesPublicas.length);
                try (var salida = intercambio.getResponseBody()) {
                    salida.write(clavesPublicas);
                }
            });
            servidor.start();
            servidor.createContext("/api", intercambio -> {
                TOKEN_INTERNO.set(intercambio.getRequestHeaders().getFirst("Authorization"));
                String ruta = intercambio.getRequestURI().getPath();
                String respuesta;
                int status = 200;
                if (ruta.equals("/api/catalog/services")) {
                    respuesta = "[{\"id\":1,\"nombre\":\"Mantención\",\"descripcion\":\"Revisión\",\"tarifa\":25000}]";
                } else if (intercambio.getRequestMethod().equals("POST")) {
                    CREACIONES.incrementAndGet();
                    CUERPO_INTERNO.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    respuesta = ORDEN; status = 201;
                } else if (ruta.equals("/api/workorders")) {
                    FILTRO_INTERNO.set(intercambio.getRequestURI().getQuery());
                    // Incluye una ajena para verificar que el BFF tampoco la expone con un MS antiguo.
                    respuesta = "[" + ORDEN + "," + ORDEN.replace("usuario-prueba", "otra-persona") + "]";
                } else if (ruta.endsWith("/2")) {
                    respuesta = ORDEN.replace("usuario-prueba", "otra-persona");
                } else if (ruta.endsWith("/404")) {
                    respuesta = "{}"; status = 404;
                } else if (ruta.endsWith("/500")) {
                    respuesta = "{}"; status = 500;
                } else { respuesta = ORDEN; }
                byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(status, bytes.length);
                try (var salida = intercambio.getResponseBody()) { salida.write(bytes); }
            });
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
