# DigitalFix — BFF

Java 21, Spring Boot 4.1.1, Spring Security y RestClient.
Integrantes: Ariel Molina y Lucas Ferrada.

## Integración implementada

Valida firma, issuer, audience, vigencia, scope access_as_user y roles
Admin/Operador/Cliente. Publica perfil, catálogo y creación/listado/detalle de
órdenes. Usa oid como identidad; falta de oid en órdenes responde 403.
Comprueba catálogo antes de crear y limita todas las consultas a órdenes propias.
No tiene conexión ni credenciales Oracle.

CATALOG_URL y WORKORDERS_URL apuntan a http://catalog:8081 y
http://workorders:8082 en docker-compose.yml. Sin Compose los valores por defecto
son localhost:8081 y localhost:8082 para desarrollo.

## Construcción y pruebas

```powershell
 .\mvnw.cmd -B verify
```

Pruebas con JWT firmados localmente y servidores HTTP de dominio simulados.
No necesitan credenciales de Entra ni Oracle. Docker compila con Java 21.

## Despliegue

[DEPLOYMENT.md](DEPLOYMENT.md) contiene la guía completa, contratos, configuración
Entra/Gateway/CORS/RDS y comandos. [VERIFICATION.md](VERIFICATION.md) registra la
verificación local y los pasos remotos pendientes.

El compose arranca los tres repositorios backend como carpetas hermanas.
Solo BFF publica 8080, accesible mediante un ALB interno y VPC Link.
GET /healthz sirve al balanceador sin JWT; no contiene datos de negocio.
No publicar /healthz ni /api/administracion como rutas de Gateway.

## Flujo de trabajo

Integrar mediante Pull Request revisado y aprobado por otro integrante.