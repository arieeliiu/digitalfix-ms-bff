# Verificación — 15 de septiembre de 2026

## Inspección inicial

Los cuatro repositorios estaban limpios (sin cambios pendientes). No se encontraron
AGENTS.md aplicables en repositorios ni ancestros. No se hicieron commits ni push.
Se conservaron los componentes existentes y se corrigió documentación contradictoria.

## Pruebas automáticas ejecutadas

| Proyecto | Resultado | Alcance |
|---|---|---|
| Frontend | 21 pruebas pasan | MSAL Interceptor real con token simulado; Bearer solo al destino configurado; POST sin identidad; UI y guards |
| BFF | 29 pruebas pasan; JAR generado | JWT firmados: firma/issuer/audience/vigencia, scope, roles, oid distinto de sub, falta de oid, propiedad, 201, servicio inexistente, errores de dominio, OPTIONS |
| Catalog | 6 pruebas pasan; JAR generado | Contratos del controlador y persistencia JPA real sobre H2 |
| Workorders | 15 pruebas pasan; JAR generado | Validación, filtro, contexto y guardar/cerrar/reabrir aplicación con H2 en disco |
| Configurador Gateway | 4 pruebas pasan | Rutas, scopes, preflight, integración privada, actualización y rechazo de rutas no revisadas; CLI simulado |

Java local 25.0.2 compilando con `release 21`. Angular compiló producción también
en Node 24: advertencia de bundle inicial 613.63 kB frente a presupuesto 500 kB;
la compilación finaliza correctamente. No se aumentó el presupuesto para ocultarla.

Comandos: `npm test -- --watch=false`, `npm run build -- --configuration production`,
`mvnw.cmd -B verify` en los tres backends y
`python -B -m unittest discover -s deploy -p 'test_*.py' -v` en BFF.

## Docker y red local

- Ambos compose validan con `docker compose config --quiet` y valores ficticios de DB.
- Se comprobó en el modelo Compose que Catalog/Workorders no publican puertos,
  las URLs usan nombres Docker y BFF no tiene variables DB.
- Imagen frontend construida y contenedor saludable en `http://localhost`, puerto 80:80.
- `nginx -t` correcto; `/healthz` 200, `/workorders` 200, asset ausente 404.
- Imagen BFF construida con Java 21. En contenedor temporal: salud 200,
  perfil sin token 401 y OPTIONS 200. El contenedor temporal fue retirado.
- Imagen Catalog construida con Java 21.
- Imagen Workorders construida con Java 21. Las cuatro imágenes terminaron correctamente.
- La primera construcción simultánea perdió conexión al daemon Docker (EOF).
  Las imágenes se reintentaron de una en una.

## Gateway real: comprobado sin credenciales

URL: `https://9ijsvq2s6j.execute-api.us-east-1.amazonaws.com`.
Comprobación HTTP del 15-09-2026, aproximadamente 05:26 UTC:

- GET `/api/perfil` sin token: **401**, correcto.
- Preflight OPTIONS `/api/workorders`, origin `http://localhost`, método POST y
  headers authorization/content-type: **401**, incorrecto; pendiente aplicar corrección.
- AWS CLI local devuelve `InvalidClientTokenId` / `UnrecognizedClientException`.
  No se aplicaron cambios remotos. Renovar credenciales temporales localmente.

## Pendiente de prueba real (no certificado por H2 ni mocks)

1. Entra: login/logout en localhost, token v2, audience API, scope, roles y oid;
   pruebas con dos usuarios Cliente y uno sin rol.
2. Permisos y saldo del Student Lab para ALB interno y VPC Link.
3. Rutas existentes, stage, integración real, CORS/OPTIONS y authorizer remoto.
4. EC2: despliegue de estas imágenes, nombres internos y conectividad Oracle.
5. SG de RDS: 1521 exclusivamente desde el SG backend, sin otras reglas amplias.
6. Oracle: creación 201 y recuperación idéntica tras reiniciar Workorders.
7. Aislamiento entre cuentas reales y token sin permisos rechazado.
8. Rotar contraseña Oracle previamente versionada; no se reescribió el historial.

Los scripts y pasos exactos están en [DEPLOYMENT.md](DEPLOYMENT.md).
`deploy/smoke.py` ejecuta la prueba real una vez desplegado; no fue ejecutado con
tokens reales en esta revisión. No almacena tokens ni contraseñas.
