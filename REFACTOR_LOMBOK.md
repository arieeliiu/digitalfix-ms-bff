# DigitalFix bff: refactor no funcional con Lombok

9 de octubre de 2026. Alcance: tipos Java, código repetitivo y documentación.
La referencia funcional es el checkout inicial, incluidos los cambios locales
previos. No se cambiaron ramas ni se crearon commits durante esta tarea.

## Responsabilidades y decisiones

El BFF mantiene Controller → Service → Client. `OrdenController` y
`CatalogoController` reciben HTTP y JWT, `OrdenService` y `CatalogoService`
orquestan, y `OrdenClient` y `CatalogoClient` encapsulan todas las llamadas HTTP.
`SecurityConfig`, `JwtAuthoritiesConverter` y `RestClientConfig` conservan los
métodos @Bean, qualifiers y configuración existentes.

Se usa @RequiredArgsConstructor en los dos controllers y dos services con
dependencias final. Los clients conservan constructores explícitos con
@Qualifier: retirar esa selección podría inyectar el RestClient incorrecto.
No se necesitan anotaciones Lombok en los records.

Se conservan la consulta global de Admin/Operador, el filtro de Cliente,
`roles`, `oid`, scopes, prefijos ROLE_/SCOPE_, STATELESS y el orden de reglas
HTTP. Se conserva también la validación manual del service: añadir @Valid al
controller cambiaría el orden de 404/400 y los mensajes de error.
Los timeouts siguen en 3 s para conexión y 10 s para lectura.

Se añadieron dos casos parametrizados que verifican listado global, ausencia
 de solicitanteId, consulta ajena y propagación del Bearer para Admin/Operador.

## Nomenclatura y archivos renombrados

Antes de editar se registraron declaraciones, imports, constructores, referencias,
generics, tests, mappers y dependencias de los tres servicios. Los renombrados
actuaron sobre identificadores Java; literals y nombres externos se conservaron.

| Tipo anterior | Tipo actual | Archivo actual |
|---|---|---|
| ControladorOrdenes | OrdenController | `src/main/java/cl/digitalfix/bff/controller/OrdenController.java` |
| ControladorCatalogo | CatalogoController | `src/main/java/cl/digitalfix/bff/controller/CatalogoController.java` |
| ControladorAcceso | AccesoController | `src/main/java/cl/digitalfix/bff/controller/AccesoController.java` |
| SaludControlador | SaludController | `src/main/java/cl/digitalfix/bff/controller/SaludController.java` |
| ServicioOrdenes | OrdenService | `src/main/java/cl/digitalfix/bff/service/OrdenService.java` |
| ServicioCatalogo | CatalogoService | `src/main/java/cl/digitalfix/bff/service/CatalogoService.java` |
| ClienteOrdenes | OrdenClient | `src/main/java/cl/digitalfix/bff/client/OrdenClient.java` |
| ClienteCatalogo | CatalogoClient | `src/main/java/cl/digitalfix/bff/client/CatalogoClient.java` |
| ConfiguracionRestClient | RestClientConfig | `src/main/java/cl/digitalfix/bff/config/client/RestClientConfig.java` |
| ConfiguracionSeguridad | SecurityConfig | `src/main/java/cl/digitalfix/bff/config/security/SecurityConfig.java` |
| ConversorJwt | JwtAuthoritiesConverter | `src/main/java/cl/digitalfix/bff/config/security/JwtAuthoritiesConverter.java` |
| ManejadorGlobalExcepciones | GlobalExceptionHandler | `src/main/java/cl/digitalfix/bff/exception/GlobalExceptionHandler.java` |
| RepuestoOrdenRequest | RepuestoRequest | `src/main/java/cl/digitalfix/bff/dto/request/RepuestoRequest.java` |

Los archivos antiguos se sustituyen por los renombrados; no quedan clases
Java duplicadas ni referencias a los tipos anteriores. Las clases Application
conservan sus nombres para mantener las referencias de arranque de Docker/IDE.
No se crean nuevas capas, clases de negocio ni abstracciones. Se crean tres
informes REFACTOR_LOMBOK.md, uno por servicio, y se actualizan los README.

## Incorporación de Lombok

pom.xml añade org.projectlombok:lombok con scope provided y optional=true,
utilizando la versión 1.18.46 gestionada por Spring Boot 4.1.1. Maven Compiler
configura annotationProcessorPaths explícitamente con ${lombok.version}.
El plugin de Spring Boot excluye Lombok del JAR de ejecución. Los Dockerfile no
necesitan cambios: ya copian pom.xml y src y compilan con JDK 21.

La integración sigue la [documentación oficial de Lombok para Maven](https://projectlombok.org/setup/maven).
No se cambia la versión de Java, Spring Boot ni de dependencias existentes.
No se añade @Data, @Builder, @EqualsAndHashCode ni @ToString.

## Compatibilidad verificada

- Mismos endpoints y métodos HTTP: 9 en BFF, 6 en Workorders y 9 en Catalog.
- Mismos campos y contratos JSON, incluyendo status, oid y repuestos.
- Misma seguridad, Bearer, roles, scopes, claims y autorizaciones.
- Mismas reglas de dominio, validaciones, consultas y transacciones.
- Mismos nombres y anotaciones JPA de tablas, columnas, colecciones y generación de IDs.
- Properties, recursos de prueba, Dockerfile, Compose y .env.example idénticos
  byte por byte al inicio de la tarea (incluidos cambios locales anteriores).
- Entidades y requests mutables conservan las firmas y visibilidad de métodos,
  getters/setters y constructores, comparadas con javap antes/después.
- Comparación de producción sin diferencias fuera de nombres, imports, formato
  y getters/setters/constructores simples reemplazados por Lombok.
- Sin ciclos de dependencias; services sin RestClient/WebClient ni records.
- Ninguna implementación nueva de RabbitMQ, Kafka o Zookeeper. Sus menciones
  documentales en Notify/Audit/Report permanecen fuera de este alcance.

## Validación

Se utilizó Maven Wrapper con Temurin JDK 21.0.12.1 temporal. El wrapper del
checkout no es ejecutable: se invocó con bash, sin modificar su contenido/permisos.
Las dependencias estaban disponibles, por lo que las comprobaciones usaron -o.

```bash
JAVA_HOME=/tmp/digitalfix-jdk21 bash ./mvnw -o clean test
JAVA_HOME=/tmp/digitalfix-jdk21 bash ./mvnw -o -DskipTests package
```

Resultado: **29 tests, 0 fallos, 0 errores, 0 omitidos; package correcto**.

Se verificó procesamiento de anotaciones, inyección de dependencias, contextos
Spring, persistencia con H2, validaciones y serialización/deserialización Jackson.
Los JAR tienen bytecode Java 21 (major 65) y no contienen Lombok en BOOT-INF/lib.
Las pruebas de seguridad del BFF usan JWT firmados y servidores HTTP locales.
Estas comprobaciones no certifican un despliegue remoto en AWS/Entra/Oracle.

## Deuda técnica detectada

| Archivo | Problema | Riesgo | Mejora futura recomendada |
|---|---|---|---|
| `src/main/resources/application.properties; docker-compose.yml` | Las URLs digitalfix.* son localhost y no referencian CATALOG_URL/WORKORDERS_URL de Compose. | Las llamadas podrían apuntar al contenedor equivocado. | Resolver el enlace de variables y propiedades en un cambio de despliegue explícito. |
| `src/main/java/cl/digitalfix/bff/service/OrdenService.java` | Validaciones manuales y comprobaciones defensivas de propiedad/formato se conservan por compatibilidad. | Sustituirlas por anotaciones o retirarlas cambia orden de errores y comportamiento con dominios antiguos. | Acordar un contrato de validaciones y permisos antes de centralizar más reglas. |
| `../digitalfix-frontend/src/app/pages/workorders/workorders.html` | Editar/Eliminar se muestran por estado, sin comprobar propiedad. | Admin/Operador pueden ver acciones sobre órdenes ajenas que el dominio rechaza con 404. | Alinear acciones de UI con la política de edición/borrado en otra tarea. |
