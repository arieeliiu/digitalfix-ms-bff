# DigitalFix: arquitectura por capas y compatibilidad

Revisión local del 5 de octubre de 2026. Este documento describe el código real
revisado en esta fase; los registros anteriores de despliegue son históricos.

## Inventario y mapa funcional

| Módulo | Implementación previa | Persistencia / dependencia |
|---|---|---|
| BFF | Spring MVC, OAuth2 Resource Server, RestClient | Catalog y Workorders por HTTP; sin JPA |
| Workorders | Spring MVC, JPA, Bean Validation, OAuth2 Resource Server | Oracle; Catalog por HTTP al asignar repuestos |
| Catalog | Spring MVC, JPA, Bean Validation | Oracle; servicios, repuestos y descuentos por orden |
| Notify | Solo README | RabbitMQ futuro, fuera del flujo activo |
| Audit | Solo README | Kafka futuro, fuera del flujo activo |
| Report | Solo README | Kafka futuro, fuera del flujo activo |

No hay configuración ni implementación de RabbitMQ, Kafka o Zookeeper en las
aplicaciones existentes. No se agregaron dependencias ni servicios Docker.

```text
Angular / MSAL ↔ Microsoft Entra ID
    │ Bearer JWT
    ▼
AWS API Gateway → BFF → Workorders → Catalog
                  │        │          │
                  └──────► Catalog    Oracle
                           Oracle
```

El BFF obtiene `oid`, reenvía el mismo Bearer token y orquesta consultas y
mutaciones. Workorders controla estados, técnico, propiedad en mutaciones,
bloqueo de órdenes y asignación de repuestos. Catalog controla stock, bloqueos,
agrupación de cantidades e idempotencia del descuento por orden.

Los tres POM conservan Java 21 y Spring Boot 4.1.1. Se conservaron los Maven
Wrappers, imágenes Docker Java 21, puertos, Compose y propiedades.
Los tests JPA usan H2; no se ejecutaron operaciones contra Oracle remoto.

## Capas finales

BFF: `controller → service → client`; `dto/request`, `dto/response`,
`config/security`, `config/client`, `exception`.

Workorders y Catalog: `controller → service → repository → entity`, con
`dto/request`, `dto/response` y `mapper`. Workorders conserva además `client`
y añade `config/client`. El mapeo de órdenes se realiza dentro de la transacción,
de modo que las colecciones JPA no se serializan en la capa HTTP.

Los mappers son funciones sin estado; no se introducen interfaces, factories,
capas genéricas ni clases preparatorias para mensajería. Las clases Application
conservan sus nombres para evitar cambios en arranque y herramientas.

## Contratos HTTP conservados

| Servicio | Método | Ruta | Éxito |
|---|---|---|---|
| BFF | GET | `/healthz` | 200 |
| BFF | GET | `/api/perfil` | 200 |
| BFF | GET | `/api/catalog/services` | 200 |
| BFF / Workorders | GET | `/api/workorders` | 200 |
| BFF / Workorders | GET | `/api/workorders/{id}` | 200 |
| BFF / Workorders | POST | `/api/workorders` | 201 |
| BFF / Workorders | PUT | `/api/workorders/{id}` | 200 |
| BFF / Workorders | PUT | `/api/workorders/{id}/status` | 200 |
| BFF / Workorders | DELETE | `/api/workorders/{id}` | 204 |
| Catalog | GET | `/api/catalog/services` | 200 |
| Catalog | POST | `/api/catalog/services` | 201 |
| Catalog | PUT | `/api/catalog/services/{id}` | 200 |
| Catalog | DELETE | `/api/catalog/services/{id}` | 204 |
| Catalog | GET | `/api/catalog/spare-parts` | 200 |
| Catalog | POST | `/api/catalog/spare-parts` | 201 |
| Catalog | PUT | `/api/catalog/spare-parts/{id}` | 200 |
| Catalog | DELETE | `/api/catalog/spare-parts/{id}` | 204 |
| Catalog | POST | `/api/catalog/spare-parts/discount-stock` | 204 |

Workorders sigue aceptando `solicitanteId` como parámetro interno: opcional al
listar y obligatorio en PUT/DELETE. En el BFF se deriva de `oid`, sin confiar en
un solicitante enviado por Angular. Las rutas públicas no cambian.

Las órdenes mantienen exactamente los campos `id`, `servicioId`, `descripcion`,
`direccion`, `solicitanteId`, `fechaCreacion`, `estado`, `tecnicoId`,
`actualizadoPor`, `fechaActualizacion` y `repuestos` (repuestoId/cantidad).
Los cambios de estado usan `status`, no `estado`.
Catalog mantiene id/nombre/descripcion/tarifa y id/nombre/descripcion/stock.

Las solicitudes de Catalog siguen aceptando `id` aunque el servidor lo ignore
al crear y utilice el ID de la ruta al actualizar. En repuestos, omitir `stock`
conserva el valor cero; `stock: null` conserva el rechazo 400. No se normalizan
textos de Catalog que antes no se normalizaban.

## Seguridad y errores

La SecurityFilterChain del BFF conserva el orden de matchers, CSRF deshabilitado,
sesiones STATELESS, `SCOPE_access_as_user`, roles `Admin`, `Operador`, `Cliente`,
y la conversión de `roles` a `ROLE_` mediante JwtAuthenticationConverter.
Los scopes se siguen convirtiendo mediante JwtGrantedAuthoritiesConverter.
Se conservan issuer, audience, `oid` y ausencia de fallback a `sub`.

Admin/Operador conservan la consulta global en el BFF; Cliente conserva el filtro
por solicitante y el ocultamiento de órdenes ajenas con 404. El cambio de estado
requiere Admin/Operador. No se introduce una política de permisos nueva.
La configuración de seguridad existente de Workorders no se modifica.

El advice del BFF se mueve a `exception/ManejadorGlobalExcepciones` conservando
ProblemDetail y mensajes: 400/404/409 de dominio se conservan; otros errores HTTP
o de formato se traducen a 502; errores de conexión, a 503. Catalog conserva su
mapa de errores de validación y `mensaje` para recursos inexistentes.
Workorders conserva ResponseStatusException; no se añade un advice que cambie
sus respuestas.

## Decisiones de compatibilidad

- El BFF mantiene las validaciones manuales existentes: reemplazarlas por
  `@Valid` antes de ejecutar el servicio cambiaría el orden de comprobaciones y
  los mensajes (por ejemplo, orden ajena con request inválido debe seguir siendo 404).
- Se mantienen la consulta al catálogo y las comprobaciones defensivas de
  propiedad/formato del BFF. No se trasladan silenciosamente a Workorders porque
  eso alteraría el comportamiento de acceso directo al dominio o de integraciones
  con versiones anteriores. Las transiciones reales y el stock siguen en dominio.
- `EstadoOrden` ya existía como enum. Se conserva su máquina de estados y la
  columna String actual; no se migra el esquema ni se cambia la serialización.
- DELETE sí se usa en Angular, en `ServicioApi.eliminarOrden` y en la página de
  órdenes. Se mantiene el borrado físico de órdenes CREADA/CANCELADA y el 409 para
  otros estados. La cancelación sería más coherente para conservar historial,
  pero sustituir DELETE requiere una decisión de producto y migración explícitas.
- El cliente HTTP de Workorders conserva su comportamiento sin nuevos timeouts
  ni reintentos. Los clients del BFF mantienen conexión 3 s y lectura 10 s.

## Deuda técnica preexistente, sin modificar en esta fase

1. El BFF lee `digitalfix.catalog-url` / `digitalfix.workorders-url` con valores
   localhost. Compose y algunos README mencionan `CATALOG_URL` / `WORKORDERS_URL`,
   pero esas variables no están referenciadas por dichas properties. Revisar esta
   discrepancia antes de desplegar; no se cambiaron nombres, valores ni Docker.
2. Workorders mantiene configuración Oracle fija y una credencial versionada.
   Revisar externalización y rotación en una tarea específica; este documento no
   reproduce valores ni modifica credenciales o conectividad.
3. La documentación histórica dice que Admin/Operador solo ven sus órdenes y que
   los dominios no validan JWT. El código del BFF permite consulta global para esos
   roles y Workorders incluye Resource Server con issuer/audience. Unificar la
   documentación de despliegue y comprobar la política real del dominio; no se
   alteró la cadena de seguridad ni su auto-configuración.
4. Catalog no valida JWT actualmente y la llamada Workorders → Catalog no lleva
   Bearer. Es un límite de confianza de red existente, pendiente de una política
   explícita de autenticación entre servicios.
5. Validar existencia del servicio al crear/editar reside en BFF, y algunos
   controles de formato están duplicados por compatibilidad. Centralizarlos más
   requiere acordar contratos para el acceso al dominio, incluyendo qué hacer con
   repuestos enviados fuera de ASIGNADA y elementos nulos en listas.
6. El descuento de stock y la actualización de la orden no tienen una transacción
   distribuida. La idempotencia por orden se conserva; fallos después del descuento,
   concurrencia sobre la misma orden y posibles compensaciones necesitan análisis
   separado. No se añade mensajería ni una saga en este refactor.
7. No hay prueba de integración con Entra, Gateway, Docker y Oracle reales.
   H2 y servidores HTTP locales no certifican esos componentes remotos.

## Verificación local

Maven Wrapper con Temurin JDK 21.0.12.1 temporal. En este checkout `mvnw` no es
ejecutable, por lo que se invoca con `bash ./mvnw` (el wrapper original).
Se ejecutaron `clean test package` y, tras ampliar cobertura, `test package`.

| Aplicación | Tests finales | Fallos / errores | Empaquetado |
|---|---:|---|---|
| BFF | 27 | 0 / 0 | Correcto |
| Workorders | 18 | 0 / 0 | Correcto |
| Catalog | 9 | 0 / 0 | Correcto |

Se comprueban contextos Spring, JWT firmado (firma, issuer, audience, vigencia,
scopes, roles, oid), Bearer hacia dominio, POST/PUT/DELETE, códigos de error,
DTOs y repuestos, validaciones de Catalog, transiciones e idempotencia de
asignación y persistencia tras reiniciar Workorders. La comparación de timestamps
persistidos contempla el redondeo propio de H2 sin modificar datos de producción.

Comparación estática con la copia previa: mismas 24 combinaciones método/ruta
(9 BFF, 6 Workorders, 9 Catalog), sin duplicadas; grafo de clases sin ciclos;
ningún service contiene RestClient/WebClient o records; controllers de dominio
sin imports de entidades; `git diff --check` correcto. POM, properties, entidades,
repositories, Docker y configuración de entorno conservados.

Notify/Audit/Report no tienen código compilable. Solo se precisó en sus README
qué queda fuera de alcance: notificaciones RabbitMQ y auditoría/reportes Kafka,
sin implementación, abstracciones, configuración ni brokers nuevos.

## Archivos movidos, renombrados y extraídos

BFF: `ControladorNegocio` se separa en `ControladorOrdenes` y
`ControladorCatalogo`; `ServiciosDominio` en `ServicioOrdenes`, `ServicioCatalogo`
y los dos clients. `ConfiguracionSeguridad` pasa de `config` a `config/security`
y extrae `ConversorJwt`. `ErroresDominio` pasa de `controller` a
`exception/ManejadorGlobalExcepciones`. Sus records se extraen y renombran:
NuevaOrden → NuevaOrdenRequest; CambioEstado → CambioEstadoRequest;
RepuestoOrden → RepuestoOrdenRequest; Orden → OrdenResponse;
ServicioCatalogo → ServicioCatalogoResponse; OrdenInterna → CrearOrdenDominioRequest.
Se añade RepuestoOrdenResponse para evitar usar un request dentro de la respuesta.

Workorders: los cuatro DTOs existentes pasan a `dto/request`;
`client/DescontarStockCatalogSolicitud` también pasa a `dto/request`.
Catalog: los dos DTOs de descuento pasan a `dto/request`.
Los tests existentes se adaptan a imports y accesores de los DTOs independientes.

### Archivos nuevos en digitalfix-ms-bff

- `src/main/java/cl/digitalfix/bff/client/ClienteCatalogo.java`
- `src/main/java/cl/digitalfix/bff/client/ClienteOrdenes.java`
- `src/main/java/cl/digitalfix/bff/config/client/ConfiguracionRestClient.java`
- `src/main/java/cl/digitalfix/bff/config/security/ConfiguracionSeguridad.java`
- `src/main/java/cl/digitalfix/bff/config/security/ConversorJwt.java`
- `src/main/java/cl/digitalfix/bff/controller/ControladorCatalogo.java`
- `src/main/java/cl/digitalfix/bff/controller/ControladorOrdenes.java`
- `src/main/java/cl/digitalfix/bff/dto/request/CambioEstadoRequest.java`
- `src/main/java/cl/digitalfix/bff/dto/request/CrearOrdenDominioRequest.java`
- `src/main/java/cl/digitalfix/bff/dto/request/NuevaOrdenRequest.java`
- `src/main/java/cl/digitalfix/bff/dto/request/RepuestoOrdenRequest.java`
- `src/main/java/cl/digitalfix/bff/dto/response/OrdenResponse.java`
- `src/main/java/cl/digitalfix/bff/dto/response/RepuestoOrdenResponse.java`
- `src/main/java/cl/digitalfix/bff/dto/response/ServicioCatalogoResponse.java`
- `src/main/java/cl/digitalfix/bff/exception/ManejadorGlobalExcepciones.java`
- `src/main/java/cl/digitalfix/bff/service/ServicioCatalogo.java`
- `src/main/java/cl/digitalfix/bff/service/ServicioOrdenes.java`

### Archivos nuevos en digitalfix-ms-workorders

- `src/main/java/cl/digitalfix/workorders/config/client/ConfiguracionRestClient.java`
- `src/main/java/cl/digitalfix/workorders/dto/request/ActualizarOrdenSolicitud.java`
- `src/main/java/cl/digitalfix/workorders/dto/request/CambiarEstadoSolicitud.java`
- `src/main/java/cl/digitalfix/workorders/dto/request/CrearOrdenSolicitud.java`
- `src/main/java/cl/digitalfix/workorders/dto/request/DescontarStockCatalogSolicitud.java`
- `src/main/java/cl/digitalfix/workorders/dto/request/RepuestoOrdenSolicitud.java`
- `src/main/java/cl/digitalfix/workorders/dto/response/OrdenTrabajoResponse.java`
- `src/main/java/cl/digitalfix/workorders/dto/response/RepuestoOrdenResponse.java`
- `src/main/java/cl/digitalfix/workorders/mapper/OrdenTrabajoMapper.java`
- `src/test/java/cl/digitalfix/workorders/OrdenTrabajoServicioTests.java`

### Archivos nuevos en digitalfix-ms-catalog

- `src/main/java/cl/digitalfix/catalog/dto/request/DescontarStockSolicitud.java`
- `src/main/java/cl/digitalfix/catalog/dto/request/RepuestoSolicitud.java`
- `src/main/java/cl/digitalfix/catalog/dto/request/RepuestoStockSolicitud.java`
- `src/main/java/cl/digitalfix/catalog/dto/request/ServicioCatalogoSolicitud.java`
- `src/main/java/cl/digitalfix/catalog/dto/response/RepuestoResponse.java`
- `src/main/java/cl/digitalfix/catalog/dto/response/ServicioCatalogoResponse.java`
- `src/main/java/cl/digitalfix/catalog/mapper/RepuestoMapper.java`
- `src/main/java/cl/digitalfix/catalog/mapper/ServicioCatalogoMapper.java`
- `src/test/java/cl/digitalfix/catalog/controller/RepuestoControladorTests.java`

Documentación: se crea este REFACTOR.md y se actualizan los README de los seis
módulos. No se eliminan implementaciones de mensajería; solo existían menciones
documentales de funcionalidades futuras.


## Actualización de nomenclatura del 9 de octubre de 2026

Los nombres descritos en este informe corresponden al refactor por capas del
5 de octubre. La convención actual y Lombok se documentan en
[REFACTOR_LOMBOK.md](REFACTOR_LOMBOK.md), conservando la lógica de esta etapa.
