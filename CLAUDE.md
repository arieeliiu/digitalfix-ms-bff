# digitalfix-ms-bff

Contexto global en `../CLAUDE.md`. Aquí va solo lo propio de este servicio.

Backend for Frontend: único punto de entrada detrás de AWS API Gateway. Valida el JWT de Entra, aplica roles y
propiedad, valida entradas y reenvía a Catalog (`http://catalog:8081`) y Workorders (`http://workorders:8082`)
propagando el **mismo Bearer**. Puerto 8080. Sin base de datos.

Clase principal: `BffApplication` (renombrada desde `DigitalfixMsBffApplication`, rename sin commitear).

## Mapa del código (`cl.digitalfix.bff`)

| Capa | Clases |
|---|---|
| `controller` | `OrdenController`, `CatalogoController`, `AccesoController` (`/api/perfil`) y `SaludController` (`/healthz`). Reciben `@AuthenticationPrincipal Jwt` |
| `service` | `OrdenService` (identidad por `oid`, filtro por dueño, validaciones) y `CatalogoService` |
| `client` | `OrdenClient` y `CatalogoClient`: **todas** las llamadas HTTP salen de aquí, con `setBearerAuth(jwt.getTokenValue())` |
| `config/security` | `SecurityConfig` (matchers por método, ruta y rol) y `JwtAuthoritiesConverter` (`scp` → `SCOPE_*`, `roles` → `ROLE_*`) |
| `config/client` | `RestClientConfig`: `catalogRestClient` y `workordersRestClient` (connect 3 s, read 10 s) |
| `exception/GlobalExceptionHandler` | `ProblemDetail`. 400, 404 y 409 de un downstream se conservan; el resto pasa a 502 y la falta de conexión a 503 |

Patrón fijo: Controller → Service → Client. Los clients mantienen constructor explícito con `@Qualifier` (no `@RequiredArgsConstructor`) para no inyectar el RestClient equivocado.

## Seguridad (lo más delicado del repo)

- Todo endpoint de negocio exige `SCOPE_access_as_user` **y** un rol. Roles: `Admin`, `Operador`, `Cliente`.
- `SecurityConfig` termina con `denyAll()` para `/api/catalog/**` y `/api/workorders/**`. **Un endpoint nuevo sin su matcher responde 403**: agrégalo explícitamente y en el orden correcto (las rutas específicas como `/*/status` van antes de `/*`).
- `/api/catalog/spare-parts/discount-stock` está bloqueado con `denyAll`: es interno entre Workorders y Catalog.
- `OPTIONS /api/**` y `GET /healthz` son públicos.
- La identidad es siempre el claim `oid` (`identidad(jwt)`). El frontend nunca manda `solicitanteId`: el BFF lo agrega al crear (`CrearOrdenDominioRequest`) y como query param al listar y cambiar estado.
- Cliente: listado filtrado por `solicitanteId` y re-filtrado en memoria. Una orden ajena responde 404.
- El rol **Auditor** del caso no existe todavía: al agregarlo, súmalo al converter, a los matchers, a los tests y a `README.md`/`DEPLOYMENT.md` (App roles en Entra).

## Endpoints

Tabla completa por rol en `README.md`. Resumen: catálogo (`/api/catalog/services`, `/spare-parts`, escritura solo Admin)
y órdenes (`/api/workorders`, `/{id}`, `/{id}/status` y `/{id}/repuestos` para Admin y Operador, `/{id}/contactos` PATCH).
Pendientes del caso: proxies a `/api/report/*` (Admin) y `/api/audit/*` (Admin y Auditor), ambos de solo lectura.

Al agregar un endpoint: DTO en `dto/request|response` (record) → método en el Client → Service con validación y rol → Controller → matcher en `SecurityConfig` → tests → `README.md`. Si usa un verbo nuevo, avisa que hay que permitirlo en el CORS y en las rutas del API Gateway (`DEPLOYMENT.md`).

## Configuración

`application.properties` trae URLs internas, `issuer-uri` y `audiences` de Entra. En Docker se sobrescriben con
`DIGITALFIX_CATALOG_URL` y `DIGITALFIX_WORKORDERS_URL`. No cambies issuer ni audience sin coordinarlo con el frontend y los otros servicios.

Al correrlo local: `DIGITALFIX_CATALOG_URL=http://localhost:8081` y `DIGITALFIX_WORKORDERS_URL=http://localhost:8082`. El BFF **no** tiene CORS: en local el frontend entra por el proxy de `ng serve` (mismo origen) y en AWS lo resuelve API Gateway. No agregues CORS al BFF para resolver un problema local.

## Tests

```powershell
.\mvnw.cmd -B verify
python deploy/smoke.py        # smoke contra un despliegue (ver DEPLOYMENT.md)
```

- `SeguridadBffTests`: JWT firmado con una clave local, downstream simulado. Cubre rol × ruta, 401/403, propagación del Bearer, 404 de orden ajena y 502/503.
- Todo matcher nuevo necesita casos permitidos **y** rechazados en `comprobarAcceso`, y la verificación de que el rechazo ocurre **antes** de llamar al servicio interno.
- Documentos de decisiones: `REFACTOR.md`, `REFACTOR_LOMBOK.md`, `VERIFICATION.md` y `DEPLOYMENT.md` (Entra, API Gateway, VPC Link, EC2).
