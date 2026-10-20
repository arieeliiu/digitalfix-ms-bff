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
import org.springframework.http.HttpMethod;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
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
    private static final AtomicReference<String> METODO_INTERNO = new AtomicReference<>();
    private static final AtomicInteger CREACIONES = new AtomicInteger();
    private static final AtomicInteger CONSULTAS_CATALOGO = new AtomicInteger();
    private static final AtomicInteger ESTADO_ESCRITURA_CATALOGO = new AtomicInteger();
    private static final AtomicReference<String> RUTA_INTERNA = new AtomicReference<>();
    private static final AtomicInteger CONSULTAS_REPUESTOS = new AtomicInteger();
    private static final AtomicInteger ESTADO_REPUESTOS = new AtomicInteger(200);
    private static final String REPUESTOS = """
        [{"id":3,"nombre":"Cable","descripcion":"Cable eléctrico","stock":10},
         {"id":7,"nombre":"Interruptor","descripcion":null,"stock":0}]
        """;
    private static final AtomicReference<String> RESPUESTA_REPUESTOS = new AtomicReference<>(REPUESTOS);
    private static final String ORDEN = """
        {"id":1,"servicioId":1,"descripcion":"Revisión","direccion":"Calle 123",
         "solicitanteId":"usuario-prueba","estado":"CREADA","fechaCreacion":"2026-09-15T00:00:00Z",
         "emailContacto":"cliente@example.org","emailTecnico":null}
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
        "sin_token,         /api/perfil,           401",
        "malformado,        /api/perfil,           401",
        "firma_invalida,    /api/perfil,           401",
        "emisor_invalido,   /api/perfil,           401",
        "audiencia_invalida,/api/perfil,           401",
        "vencido,           /api/perfil,           401",
        "futuro,            /api/perfil,           401",
        "sin_scope,         /api/perfil,           403",
        "Operador,          /api/perfil,           200",
        "Cliente,           /api/perfil,           200",
        "Admin,             /api/perfil,           200",
        "sin_token,         /api/workorders,       401",
        "firma_invalida,    /api/catalog/services, 401",
        "sin_scope,         /api/workorders,       403",
        "sin_rol,           /api/catalog/services, 403",
        "sin_rol,           /api/perfil,           403",
        "sin_oid,           /api/workorders,       403"
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

    @ParameterizedTest
    @CsvSource({"Admin", "Operador"})
    void consultaRepuestosConContratoYBearer(String rol) throws Exception {
        String token = crearToken(rol);
        int antes = CONSULTAS_REPUESTOS.get();
        cliente.perform(get("/api/catalog/spare-parts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Cable"))
                .andExpect(jsonPath("$[0].descripcion").value("Cable eléctrico"))
                .andExpect(jsonPath("$[0].stock").value(10))
                .andExpect(jsonPath("$[1].descripcion").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].stock").value(0));
        assertEquals(antes + 1, CONSULTAS_REPUESTOS.get());
        assertEquals("GET", METODO_INTERNO.get());
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());
    }

    @ParameterizedTest
    @CsvSource({"Cliente,403", "sin_scope,403", "sin_rol,403", "sin_token,401"})
    void rechazaRepuestosSinPermisosAntesDeConsultarCatalogo(String caso, int estado) throws Exception {
        int antes = CONSULTAS_REPUESTOS.get();
        var solicitud = get("/api/catalog/spare-parts");
        if (!caso.equals("sin_token")) {
            solicitud.header("Authorization", "Bearer " + crearToken(caso));
        }
        cliente.perform(solicitud).andExpect(status().is(estado));
        assertEquals(antes, CONSULTAS_REPUESTOS.get());
    }

    @Test
    void repuestosSinRespuestaDevuelve502() throws Exception {
        ESTADO_REPUESTOS.set(204);
        try {
            cliente.perform(get("/api/catalog/spare-parts").header("Authorization", "Bearer " + crearToken("Admin")))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.detail").value("Catálogo sin respuesta"));
        } finally {
            ESTADO_REPUESTOS.set(200);
        }
    }

    @Test
    void catalogoSinRepuestosDevuelveListaVacia() throws Exception {
        RESPUESTA_REPUESTOS.set("[]");
        try {
            cliente.perform(get("/api/catalog/spare-parts").header("Authorization", "Bearer " + crearToken("Admin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        } finally {
            RESPUESTA_REPUESTOS.set(REPUESTOS);
        }
    }

    @Test
    void falloDeCatalogoAlConsultarRepuestosDevuelve502() throws Exception {
        ESTADO_REPUESTOS.set(500);
        try {
            cliente.perform(get("/api/catalog/spare-parts").header("Authorization", "Bearer " + crearToken("Operador")))
                    .andExpect(status().isBadGateway());
        } finally {
            ESTADO_REPUESTOS.set(200);
        }
    }

    @Test
    void saludYOpcionesNoExigenToken() throws Exception {
        cliente.perform(get("/healthz")).andExpect(status().isOk());
        cliente.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/workorders"))
            .andExpect(status().isOk());
    }

    @Test
    void flujoCatalogoCrearYConsultarConJwtFirmado() throws Exception {
        String tokenCatalogo = crearToken("Cliente");
        cliente.perform(get("/api/catalog/services").header("Authorization", "Bearer " + tokenCatalogo))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].tarifa").value(25000));
        assertEquals("Bearer " + tokenCatalogo, TOKEN_INTERNO.get());

        String token = crearToken("Cliente");
        cliente.perform(post("/api/workorders").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"servicioId":1,"descripcion":"Revisión","direccion":"Calle 123","solicitanteId":"otra-persona","emailContacto":"cliente@example.org"}
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

    @ParameterizedTest
    @CsvSource({"Admin", "Operador"})
    void conservaConsultaGlobalYAccesoAOrdenAjena(String rol) throws Exception {
        String token = crearToken(rol);
        cliente.perform(get("/api/workorders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        assertNull(FILTRO_INTERNO.get());
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());

        cliente.perform(get("/api/workorders/2").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitanteId").value("otra-persona"));
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());
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
                {"servicioId":999,"descripcion":"Revisión","direccion":"Calle 123","emailContacto":"cliente@example.org"}
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

    @Test
    void actualizarYEliminarConservanContratoYBearer() throws Exception {
        String token = crearToken("Cliente");
        cliente.perform(put("/api/workorders/1").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"servicioId":1,"descripcion":" Revisión ","direccion":" Calle 123 "}
                    """))
                .andExpect(status().isOk());
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());
        assertNull(FILTRO_INTERNO.get());
        assertTrue(CUERPO_INTERNO.get().contains("\"descripcion\":\"Revisión\""));
        assertTrue(CUERPO_INTERNO.get().contains("\"direccion\":\"Calle 123\""));

        cliente.perform(delete("/api/workorders/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertEquals("DELETE", METODO_INTERNO.get());
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());
        assertNull(FILTRO_INTERNO.get());
    }

    @ParameterizedTest
    @CsvSource({"Admin,200,204", "Operador,404,404", "Cliente,404,404", "sin_oid,403,403", "sin_rol,403,403", "sin_scope,403,403"})
    void soloAdminModificaOrdenAjenaSinSuplantarPropietario(String rol, int edicion, int eliminacion) throws Exception {
        String token = crearToken(rol);
        cliente.perform(put("/api/workorders/2").param("solicitanteId", "otra-persona").param("rol", "Admin")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"servicioId\":1,\"descripcion\":\"Cambio\",\"direccion\":\"Calle\",\"roles\":[\"Admin\"]}"))
                .andExpect(status().is(edicion));
        if (edicion == 200) {
            assertNull(FILTRO_INTERNO.get());
            assertEquals("Bearer " + token, TOKEN_INTERNO.get());
            assertFalse(CUERPO_INTERNO.get().contains("roles"));
        }
        cliente.perform(delete("/api/workorders/2").param("solicitanteId", "otra-persona").param("admin", "true")
                .header("Authorization", "Bearer " + token)).andExpect(status().is(eliminacion));
        if (eliminacion == 204) assertNull(FILTRO_INTERNO.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operador", "Cliente"})
    void estadosRestringenEdicionYEliminacionParaTodos(String rol) throws Exception {
        String token = crearToken(rol);
        for (int id = 3; id <= 7; id++) {
            cliente.perform(put("/api/workorders/" + id).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"servicioId\":1,\"descripcion\":\"Cambio\",\"direccion\":\"Calle\"}"))
                    .andExpect(status().isConflict());
            cliente.perform(delete("/api/workorders/" + id).header("Authorization", "Bearer " + token))
                    .andExpect(status().is(id == 4 ? 204 : 409));
        }
    }

    @Test
    void cambioEstadoMantieneRolesPayloadYBearer() throws Exception {
        String cuerpo = """
            {"status":"ASIGNADA","tecnicoId":"tecnico-1","emailTecnico":"tecnico@example.org","repuestos":[{"repuestoId":4,"cantidad":2}]}
            """;
        cliente.perform(put("/api/workorders/1/status")
                .header("Authorization", "Bearer " + crearToken("Cliente"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isForbidden());

        String token = crearToken("Operador");
        cliente.perform(put("/api/workorders/1/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());
        assertEquals("solicitanteId=usuario-prueba", FILTRO_INTERNO.get());
        assertTrue(CUERPO_INTERNO.get().contains("\"status\":\"ASIGNADA\""));
        assertTrue(CUERPO_INTERNO.get().contains("\"repuestoId\":4"));
        assertTrue(CUERPO_INTERNO.get().contains("\"cantidad\":2"));
    }

    @Test
    void conservarDetalleDeValidacionYOrdenDeComprobaciones() throws Exception {
        String token = crearToken("Cliente");
        cliente.perform(post("/api/workorders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"emailContacto\":\"cliente@example.org\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Servicio, descripción y dirección válidos son obligatorios"));
        cliente.perform(put("/api/workorders/2").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Orden no encontrada"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"services", "spare-parts"})
    void adminPuedeCrudCatalogoConContratoYBearer(String recurso) throws Exception {
        String token = crearToken("Admin");
        String ruta = "/api/catalog/" + recurso;
        String campo = recurso.equals("services") ? "tarifa" : "stock";
        String cuerpo = recurso.equals("services")
                ? "{\"id\":31,\"nombre\":\"Mantención\",\"descripcion\":null,\"tarifa\":12345.67}"
                : "{\"id\":31,\"nombre\":\"Cable\",\"descripcion\":\"Cable eléctrico\",\"stock\":12}";

        cliente.perform(get(ruta).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals("GET", METODO_INTERNO.get());
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());
        assertEquals(ruta, RUTA_INTERNA.get());

        for (String metodo : List.of("POST", "PUT")) {
            String destino = metodo.equals("POST") ? ruta : ruta + "/31";
            cliente.perform(request(HttpMethod.valueOf(metodo), destino)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().is(metodo.equals("POST") ? 201 : 200))
                    .andExpect(jsonPath("$.id").value(31))
                    .andExpect(jsonPath("$.nombre").value(recurso.equals("services") ? "Mantención" : "Cable"))
                    .andExpect(jsonPath("$." + campo).value(recurso.equals("services") ? 12345.67 : 12));
            org.skyscreamer.jsonassert.JSONAssert.assertEquals(cuerpo, CUERPO_INTERNO.get(), true);
            assertEquals(metodo, METODO_INTERNO.get());
            assertEquals(destino, RUTA_INTERNA.get());
            assertEquals("Bearer " + token, TOKEN_INTERNO.get());
        }

        cliente.perform(delete(ruta + "/31").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
        assertEquals("DELETE", METODO_INTERNO.get());
        assertEquals(ruta + "/31", RUTA_INTERNA.get());
        assertEquals("Bearer " + token, TOKEN_INTERNO.get());
    }

    @ParameterizedTest
    @CsvSource({"Operador,200,200,403", "Cliente,200,403,403", "sin_scope,403,403,403", "sin_rol,403,403,403", "sin_token,401,401,401"})
    void permisosCatalogoPorRecursoYMetodo(String caso, int lecturaServicios, int lecturaRepuestos, int escritura) throws Exception {
        for (String recurso : List.of("services", "spare-parts")) {
            int lectura = recurso.equals("services") ? lecturaServicios : lecturaRepuestos;
            for (String metodo : List.of("GET", "POST", "PUT", "DELETE")) {
                String ruta = "/api/catalog/" + recurso + (List.of("PUT", "DELETE").contains(metodo) ? "/31" : "");
                var solicitud = request(HttpMethod.valueOf(metodo), ruta)
                        .contentType(MediaType.APPLICATION_JSON).content("{}");
                if (!caso.equals("sin_token")) solicitud.header("Authorization", "Bearer " + crearToken(caso));
                int antes = CONSULTAS_CATALOGO.get();
                int esperado = metodo.equals("GET") ? lectura : escritura;
                cliente.perform(solicitud).andExpect(status().is(esperado));
                assertEquals(antes + (esperado == 200 ? 1 : 0), CONSULTAS_CATALOGO.get());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operador", "Cliente"})
    void descuentoStockNuncaSeExpone(String rol) throws Exception {
        int antes = CONSULTAS_CATALOGO.get();
        for (String metodo : List.of("GET", "POST", "PUT", "DELETE")) {
            cliente.perform(request(HttpMethod.valueOf(metodo), "/api/catalog/spare-parts/discount-stock")
                    .header("Authorization", "Bearer " + crearToken(rol))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        assertEquals(antes, CONSULTAS_CATALOGO.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409})
    void conservaErroresDeCatalogoEnEscrituras(int codigo) throws Exception {
        ESTADO_ESCRITURA_CATALOGO.set(codigo);
        try {
            for (String recurso : List.of("services", "spare-parts")) {
                for (String metodo : List.of("POST", "PUT", "DELETE")) {
                    String ruta = "/api/catalog/" + recurso + (metodo.equals("POST") ? "" : "/31");
                    cliente.perform(request(HttpMethod.valueOf(metodo), ruta)
                            .header("Authorization", "Bearer " + crearToken("Admin"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andExpect(status().is(codigo))
                            .andExpect(jsonPath("$.detail").value("El servicio rechazó la operación solicitada"));
                }
            }
        } finally {
            ESTADO_ESCRITURA_CATALOGO.set(0);
        }
    }

    @Test
    void escriturasSinRespuestaDeCatalogoDevuelven502() throws Exception {
        ESTADO_ESCRITURA_CATALOGO.set(204);
        try {
            for (String recurso : List.of("services", "spare-parts")) {
                for (String metodo : List.of("POST", "PUT")) {
                    String ruta = "/api/catalog/" + recurso + (metodo.equals("POST") ? "" : "/31");
                    cliente.perform(request(HttpMethod.valueOf(metodo), ruta)
                            .header("Authorization", "Bearer " + crearToken("Admin"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andExpect(status().isBadGateway())
                            .andExpect(jsonPath("$.detail").value("Catálogo sin respuesta"));
                }
            }
        } finally {
            ESTADO_ESCRITURA_CATALOGO.set(0);
        }
    }

    @Test
    void conservaStockOmitidoYCeroYNullExplicitoSegunContratoCatalogo() throws Exception {
        for (String stock : List.of("", ",\"stock\":0", ",\"stock\":null")) {
            String cuerpo = "{\"id\":31,\"nombre\":\"Cable\",\"descripcion\":\"Cable eléctrico\"" + stock + "}";
            cliente.perform(post("/api/catalog/spare-parts")
                    .header("Authorization", "Bearer " + crearToken("Admin"))
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated());
            String esperado = stock.isEmpty() ? cuerpo.replace("}", ",\"stock\":0}") : cuerpo;
            org.skyscreamer.jsonassert.JSONAssert.assertEquals(esperado, CUERPO_INTERNO.get(), true);
        }
    }

    @Test
    void correosViajanSinAceptarIdentidadDelCliente() throws Exception {
        cliente.perform(post("/api/workorders").header("Authorization","Bearer "+crearToken("Cliente"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"servicioId":1,"descripcion":"Revisión","direccion":"Calle","emailContacto":"cliente@example.org","solicitanteId":"impostor"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.emailContacto").value("cliente@example.org"))
                .andExpect(jsonPath("$.emailTecnico").value(org.hamcrest.Matchers.nullValue()));
        assertTrue(CUERPO_INTERNO.get().contains("cliente@example.org"));
        assertFalse(CUERPO_INTERNO.get().contains("impostor"));
        cliente.perform(put("/api/workorders/1/status").header("Authorization","Bearer "+crearToken("Operador"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"status":"ASIGNADA","tecnicoId":"t1","emailTecnico":"tecnico@example.org","repuestos":[]}
                """))
                .andExpect(status().isOk());
        assertTrue(CUERPO_INTERNO.get().contains("tecnico@example.org"));
    }

    @ParameterizedTest
    @CsvSource({"sin_token,401", "sin_scope,403", "sin_rol,403", "sin_oid,403", "Cliente,200", "Operador,200", "Admin,200"})
    void contactosTienenReglaExplicitaDeSeguridad(String caso, int codigo) throws Exception {
        var solicitud=request(HttpMethod.PATCH,"/api/workorders/1/contactos")
                .contentType(MediaType.APPLICATION_JSON).content("{\"emailContacto\":\"nuevo@example.org\",\"actorId\":\"impostor\"}");
        String token=caso.equals("sin_token") ? null : crearToken(caso);
        if (token!=null) solicitud.header("Authorization","Bearer "+token);
        cliente.perform(solicitud).andExpect(status().is(codigo));
        if (codigo==200) {
            assertEquals("PATCH",METODO_INTERNO.get()); assertNull(FILTRO_INTERNO.get());
            assertEquals("Bearer "+token,TOKEN_INTERNO.get());
            assertFalse(CUERPO_INTERNO.get().contains("impostor"));
        }
    }

    @Test
    void contactosRespetanPropiedadTecnicoYLimpiar() throws Exception {
        cliente.perform(request(HttpMethod.PATCH,"/api/workorders/2/contactos")
                .header("Authorization","Bearer "+crearToken("Cliente"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"emailContacto\":\"x@example.org\"}"))
                .andExpect(status().isNotFound());
        cliente.perform(request(HttpMethod.PATCH,"/api/workorders/1/contactos")
                .header("Authorization","Bearer "+crearToken("Cliente"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"emailTecnico\":\"\"}"))
                .andExpect(status().isForbidden());
        cliente.perform(request(HttpMethod.PATCH,"/api/workorders/2/contactos")
                .header("Authorization","Bearer "+crearToken("Admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"emailTecnico\":\"\"}"))
                .andExpect(status().isOk());
        var body=tools.jackson.databind.json.JsonMapper.builder().build().readTree(CUERPO_INTERNO.get());
        assertEquals("",body.get("emailTecnico").asText());
        assertFalse(body.has("emailContacto"));
    }

    @ParameterizedTest
    @ValueSource(strings={"no-es-correo", "a@", "demasiado-largo"})
    void rechazaCorreosInvalidosEnLosTresFlujos(String valor) throws Exception {
        if (valor.equals("demasiado-largo")) valor="a".repeat(250)+"@example.org";
        String token=crearToken("Admin");
        cliente.perform(post("/api/workorders").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"servicioId\":1,\"descripcion\":\"R\",\"direccion\":\"C\",\"emailContacto\":\""+valor+"\"}"))
                .andExpect(status().isBadRequest());
        cliente.perform(put("/api/workorders/1/status").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ASIGNADA\",\"emailTecnico\":\""+valor+"\"}"))
                .andExpect(status().isBadRequest());
        cliente.perform(request(HttpMethod.PATCH,"/api/workorders/1/contactos").header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"emailContacto\":\""+valor+"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void correosObligatoriosYOmisionDeContactosConservanContrato() throws Exception {
        String token = crearToken("Admin");
        for (String valor : List.of("omitido", "null", "\"\"", "\"   \"")) {
            String contacto = valor.equals("omitido") ? "" : ",\"emailContacto\":" + valor;
            cliente.perform(post("/api/workorders").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"servicioId\":1,\"descripcion\":\"R\",\"direccion\":\"C\"" + contacto + "}"))
                    .andExpect(status().isBadRequest());
            String tecnico = valor.equals("omitido") ? "" : ",\"emailTecnico\":" + valor;
            cliente.perform(put("/api/workorders/1/status").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"ASIGNADA\",\"tecnicoId\":\"t\"" + tecnico + "}"))
                    .andExpect(status().isBadRequest());
            if (!valor.equals("omitido"))
                cliente.perform(request(HttpMethod.PATCH, "/api/workorders/1/contactos").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailContacto\":" + valor + "}"))
                        .andExpect(status().isBadRequest());
        }
        cliente.perform(request(HttpMethod.PATCH, "/api/workorders/1/contactos").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isOk());
        assertEquals("{}", CUERPO_INTERNO.get());
        cliente.perform(put("/api/workorders/5/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ASIGNADA\"}")).andExpect(status().isOk());
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
                METODO_INTERNO.set(intercambio.getRequestMethod());
                String ruta = intercambio.getRequestURI().getPath();
                RUTA_INTERNA.set(ruta);
                if (ruta.startsWith("/api/catalog/")) CONSULTAS_CATALOGO.incrementAndGet();
                String respuesta;
                int status = 200;
                if (ruta.startsWith("/api/catalog/") && !intercambio.getRequestMethod().equals("GET")) {
                    CUERPO_INTERNO.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    status = ESTADO_ESCRITURA_CATALOGO.get();
                    if (status == 0) {
                        status = switch (intercambio.getRequestMethod()) {
                            case "POST" -> 201;
                            case "DELETE" -> 204;
                            default -> 200;
                        };
                    }
                    if (status == 204) {
                        intercambio.sendResponseHeaders(204, -1);
                        intercambio.close();
                        return;
                    }
                    respuesta = status >= 400 ? "{}" : CUERPO_INTERNO.get();
                } else if (ruta.equals("/api/catalog/services")) {
                    respuesta = "[{\"id\":1,\"nombre\":\"Mantención\",\"descripcion\":\"Revisión\",\"tarifa\":25000}]";
                } else if (ruta.equals("/api/catalog/spare-parts")) {
                    CONSULTAS_REPUESTOS.incrementAndGet();
                    status = ESTADO_REPUESTOS.get();
                    if (status == 204) {
                        intercambio.sendResponseHeaders(204, -1);
                        intercambio.close();
                        return;
                    }
                    respuesta = RESPUESTA_REPUESTOS.get();
                } else if (intercambio.getRequestMethod().equals("POST")) {
                    CREACIONES.incrementAndGet();
                    CUERPO_INTERNO.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    respuesta = ORDEN; status = 201;
                } else if (intercambio.getRequestMethod().equals("PATCH")) {
                    FILTRO_INTERNO.set(intercambio.getRequestURI().getQuery());
                    CUERPO_INTERNO.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    respuesta = ORDEN;
                } else if (intercambio.getRequestMethod().equals("PUT")) {
                    FILTRO_INTERNO.set(intercambio.getRequestURI().getQuery());
                    CUERPO_INTERNO.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    respuesta = ORDEN;
                } else if (intercambio.getRequestMethod().equals("DELETE")) {
                    FILTRO_INTERNO.set(intercambio.getRequestURI().getQuery());
                    intercambio.sendResponseHeaders(204, -1);
                    intercambio.close();
                    return;
                } else if (ruta.equals("/api/workorders")) {
                    FILTRO_INTERNO.set(intercambio.getRequestURI().getQuery());
                    // Incluye una ajena para verificar que el BFF tampoco la expone con un MS antiguo.
                    respuesta = "[" + ORDEN + "," + ORDEN.replace("usuario-prueba", "otra-persona") + "]";
                } else if (ruta.endsWith("/2")) {
                    respuesta = ORDEN.replace("usuario-prueba", "otra-persona");
                } else if (ruta.matches("/api/workorders/[3-7]")) {
                    String estado = switch (ruta.charAt(ruta.length() - 1)) {
                        case '3' -> "CERRADA";
                        case '4' -> "CANCELADA";
                        case '5' -> "ASIGNADA";
                        case '6' -> "EN_DESPLAZAMIENTO";
                        default -> "EN_EJECUCION";
                    };
                    respuesta = ORDEN.replace("CREADA", estado);
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
