# DigitalFix: despliegue mínimo y demostración

## Arquitectura y decisiones

Angular/Nginx local `http://localhost` → Entra/MSAL → HTTP API Gateway → VPC Link →
ALB interno → BFF:8080 → Catalog:8081 y Workorders:8082 en Docker → Oracle RDS:1521.
El ALB interno permite que el BFF reciba tráfico únicamente desde Gateway sin dominio
ni certificado propio. No se usan CloudFront, dominios personalizados ni proveedores externos.
Comprobar disponibilidad de ALB y VPC Link en el Student Lab antes de provisionar;
son recursos con costo del saldo del laboratorio. Si el laboratorio los prohíbe,
este despliegue privado queda bloqueado: no abrir 8080 a Internet como sustituto.

El bridge Docker necesita salida hacia RDS y Entra (descubrimiento OIDC/JWKS por HTTPS).
Por eso no usa `internal: true`. La privacidad de Catalog/Workorders se obtiene
sin `ports`, con acceso entre contenedores y Security Groups restrictivos.
Los servicios internos confían en el BFF y no validan JWT por sí mismos.
El BFF no tiene driver, credenciales ni acceso de aplicación a Oracle.

`oid` del JWT validado es el solicitante. Se valida un único issuer/tenant; no se
usan email, nombre ni `sub` como fallback. Sin oid válido, órdenes responde 403.
Órdenes creadas anteriormente con `sub` necesitan una migración explícita sub→oid
verificada por un administrador; no se reasignan ni se borran automáticamente.

Los roles técnicos exactos son `Admin`, `Operador`, `Cliente`. Los tres consultan
el catálogo y crean/listan/consultan **solo sus propias órdenes** en este alcance.
No se introduce acceso global a órdenes para Admin/Operador. El endpoint auxiliar
`/api/administracion` sigue siendo Admin, pero no se publica en Gateway.

## 1. Entra ID (portal)

En el tenant `762b016c-dc33-4db0-ad42-44f32afe71f4`:

1. App registrations → DigitalFix API (`85329d90-58f8-4317-a820-452599b3b04c`).
2. Manifest → `api.requestedAccessTokenVersion: 2`. Guardar sin sustituir el resto del manifiesto.
3. Expose an API → Application ID URI `api://85329d90-58f8-4317-a820-452599b3b04c`.
   Scope delegado habilitado `access_as_user`.
4. App roles → crear/habilitar valores exactos `Admin`, `Operador`, `Cliente`,
   con miembros de tipo Users/Groups. El nombre visible puede ser Administrador;
   el **value** enviado en el token debe ser `Admin`.
5. Enterprise applications → DigitalFix API → Users and groups: asignar usuarios
   a los roles de la **API**. Para la demostración usar dos usuarios Cliente distintos.
6. DigitalFix Frontend (`0a57e0f7-1a4f-40f2-9011-eac64a4c49c6`) → Authentication
   → Add a platform → Single-page application → redirect URI `http://localhost`.
   MSAL utiliza este origen también para postLogoutRedirectUri.
7. API permissions → My APIs → DigitalFix API → Delegated `access_as_user`;
   conceder consentimiento de administrador según la configuración del tenant.
   No crear un client secret para Angular ni activar flujo implícito.
8. Tras cambiar permisos, cerrar sesión y volver a entrar. Comprobar el access token
   localmente, sin subirlo a decodificadores: `ver=2.0`, issuer de abajo, audience
   de la API, `scp` incluye `access_as_user`, `roles` y `oid`. El ID token no sirve para la API.

## 2. Preparar frontend local

Desde `digitalfix-frontend`, con Docker Desktop usando Linux containers:

```powershell
docker compose up -d --build
docker compose exec frontend nginx -t
curl.exe -i http://localhost/healthz
curl.exe -I http://localhost/workorders
```

Abrir `http://localhost`. Publica `80:80`; Nginx sirve la SPA y no hace proxy.
La API está fijada en `src/environments/environment.ts` a
`https://9ijsvq2s6j.execute-api.us-east-1.amazonaws.com` (stage `$default`).
Cambiar ese valor requiere reconstruir la imagen. No usar `localhost:4200` para la demo.

## 3. EC2, Docker y Oracle

Usar una EC2 Linux en la misma VPC que RDS y los recursos de integración.
Necesita salida a Internet para construir imágenes y validar las claves de Entra.
Puede usar subnet pública con IP pública y sin entrada pública de aplicación;
eso evita necesitar NAT Gateway. SSH se limita a la IP de administración.
RDS debe tener `PubliclyAccessible=false` y estar disponible.

Instalación de Docker en Amazon Linux 2023 (omitir si ya está instalado):

```bash
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
# Cerrar y volver a abrir SSH para aplicar el grupo.
```

Docker Compose como plugin debe estar instalado: verificar `docker compose version`.
Si falta, instalación manual según la [guía oficial](https://docs.docker.com/compose/install/linux/):

```bash
mkdir -p "$HOME/.docker/cli-plugins"
curl -fSL "https://github.com/docker/compose/releases/download/v5.5.0/docker-compose-linux-$(uname -m)" -o "$HOME/.docker/cli-plugins/docker-compose"
chmod +x "$HOME/.docker/cli-plugins/docker-compose"
docker compose version
```

La instalación manual requiere actualizaciones manuales. No usar docker-compose v1.

Colocar los tres repositorios backend actualizados como carpetas hermanas
(clonar con sus URLs reales o transferir los archivos revisados). Desde su padre:

```bash
cd digitalfix-ms-bff
umask 077
cp -n .env.example .env
chmod 600 .env
nano .env
```

Completar `.env` **solo en EC2**:

| Variable Compose | Destino |
|---|---|
| DB_HOST | Endpoint real de Oracle RDS, sin protocolo ni puerto |
| DB_PORT | 1521 |
| DB_SERVICE | Service name real de Oracle (confirmar ORCL) |
| CATALOG_DB_USERNAME / CATALOG_DB_PASSWORD | DB_USERNAME / DB_PASSWORD de Catalog |
| WORKORDERS_DB_USERNAME / WORKORDERS_DB_PASSWORD | DB_USERNAME / DB_PASSWORD de Workorders |

Usar comillas simples en valores `.env` que contengan `$` o `#`.
El JDBC resultante es `jdbc:oracle:thin:@//HOST:1521/SERVICE`.
Cada usuario Oracle necesita CREATE SESSION, CREATE TABLE, CREATE SEQUENCE y cuota
en su tablespace para `ddl-auto=update`. Preferir esquemas separados de aplicación.
Hibernate actualiza tablas sin borrarlas; H2 está limitado a dependencias de prueba.
No cambiar a create/create-drop en producción. No publicar 1521 fuera del SG backend.

Se encontraron credenciales versionadas en la configuración anterior: rotar esa
contraseña en RDS y revisar su historial antes de compartir los repositorios.
La corrección actual no reescribe el historial ni rota la contraseña remota.

```bash
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail 80 bff catalog workorders
curl -i http://localhost:8080/healthz
curl -i http://localhost:8080/api/perfil  # 401 sin token
```

`depends_on` ordena el arranque, no garantiza disponibilidad de Oracle: esperar
los mensajes Started de los servicios antes de la demo. No imprimir `docker compose
config` sin `--quiet` ni compartir `docker inspect`, porque contienen credenciales.
En `docker compose ps`, solo BFF debe publicar `8080`; Catalog y Workorders no publican puertos.

## 4. Red privada para Gateway

Desde una terminal Bash con AWS CLI v2 y credenciales **temporales** del Student Lab
(incluido session token), definir los identificadores reales. No escribirlos en Git.

```bash
export AWS_DEFAULT_REGION=us-east-1
export AWS_PAGER=''
aws sts get-caller-identity
export VPC_ID='<vpc-id>'
export SUBNET_A='<subnet-az-a>'
export SUBNET_B='<subnet-az-b>'
export EC2_ID='<instancia-backend>'
export BACKEND_SG='<sg-exclusivo-ec2-backend>'
export RDS_SG='<sg-rds>'
```

Las subnets del ALB deben estar en dos AZ diferentes. Crear una sola vez, o reutilizar
recursos existentes equivalentes obteniendo sus IDs en consola:

```bash
LINK_SG=$(aws ec2 create-security-group --vpc-id "$VPC_ID" --group-name digitalfix-link --description 'DigitalFix VPC Link' --query GroupId --output text)
ALB_SG=$(aws ec2 create-security-group --vpc-id "$VPC_ID" --group-name digitalfix-alb --description 'DigitalFix internal ALB' --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$ALB_SG" --protocol tcp --port 80 --source-group "$LINK_SG"
aws ec2 authorize-security-group-ingress --group-id "$BACKEND_SG" --protocol tcp --port 8080 --source-group "$ALB_SG"
aws ec2 authorize-security-group-ingress --group-id "$RDS_SG" --protocol tcp --port 1521 --source-group "$BACKEND_SG"
ALB_ARN=$(aws elbv2 create-load-balancer --name digitalfix-internal --scheme internal --type application --subnets "$SUBNET_A" "$SUBNET_B" --security-groups "$ALB_SG" --query 'LoadBalancers[0].LoadBalancerArn' --output text)
TG_ARN=$(aws elbv2 create-target-group --name digitalfix-bff --protocol HTTP --port 8080 --vpc-id "$VPC_ID" --target-type instance --health-check-path /healthz --matcher HttpCode=200 --query 'TargetGroups[0].TargetGroupArn' --output text)
aws elbv2 register-targets --target-group-arn "$TG_ARN" --targets "Id=$EC2_ID,Port=8080"
LISTENER_ARN=$(aws elbv2 create-listener --load-balancer-arn "$ALB_ARN" --protocol HTTP --port 80 --default-actions "Type=forward,TargetGroupArn=$TG_ARN" --query 'Listeners[0].ListenerArn' --output text)
VPC_LINK_ID=$(aws apigatewayv2 create-vpc-link --name digitalfix-link --subnet-ids "$SUBNET_A" "$SUBNET_B" --security-group-ids "$LINK_SG" --query VpcLinkId --output text)
aws apigatewayv2 get-vpc-link --vpc-link-id "$VPC_LINK_ID"
aws elbv2 describe-target-health --target-group-arn "$TG_ARN"
```

Esperar `AVAILABLE` en VPC Link y `healthy` en el target. Revisar **todos** los SG
asociados a EC2 y RDS: las reglas se suman. Retirar reglas anteriores para 8080
desde Internet y cualquier permiso 1521 que no provenga exclusivamente de BACKEND_SG.
No abrir 8081/8082. Confirmar las rutas de subnets, salida de SG y NACL si falla la conexión.

```bash
aws ec2 describe-security-group-rules --filters "Name=group-id,Values=$BACKEND_SG,$RDS_SG,$ALB_SG,$LINK_SG"
# Tras revisar una regla demasiado amplia y copiar su ID real:
# aws ec2 revoke-security-group-ingress --group-id "$RDS_SG" --security-group-rule-ids '<sgr-id>'
```

## 5. Configurar HTTP API existente

El script versionado crea o actualiza recursos con nombres DigitalFix y conserva
el header Authorization. Se detiene antes de cambios si hay rutas fuera de las
cinco solicitadas y OPTIONS: revisarlas en consola y retirar las que ya no correspondan.
No elimina rutas automáticamente. Ejecutar desde `digitalfix-ms-bff`:

```bash
aws apigatewayv2 get-routes --api-id 9ijsvq2s6j
python3 deploy/gateway.py --listener-arn "$LISTENER_ARN" --vpc-link-id "$VPC_LINK_ID"
aws apigatewayv2 get-routes --api-id 9ijsvq2s6j
aws apigatewayv2 get-authorizers --api-id 9ijsvq2s6j
aws apigatewayv2 get-integrations --api-id 9ijsvq2s6j
```

Equivalente en consola: las cinco rutas de la tabla deben apuntar a **una integración
HTTP_PROXY privada al listener del ALB** con método ANY, payload 1.0,
`overwrite:path = $request.path`, y JWT Authorizer:

- Identity source: `$request.header.Authorization`.
- Issuer: `https://login.microsoftonline.com/762b016c-dc33-4db0-ad42-44f32afe71f4/v2.0`.
- Audience: `85329d90-58f8-4317-a820-452599b3b04c`.
- Scope obligatorio **en cada ruta**: `access_as_user`.
- Stage `$default`, auto-deploy habilitado, sin prefijo de stage en la URL.
- CORS: origin `http://localhost`; methods GET, POST, OPTIONS;
  headers Authorization, Content-Type; credentials desactivado.
- `OPTIONS /{proxy+}` → misma integración, Authorization NONE, sin scope requerido.
  Gateway responde preflight sin exigir JWT. No crear una ruta `$default` de captura general.

## 6. Contratos JSON

| Método y ruta pública | Respuesta |
|---|---|
| GET /api/perfil | 200 `{"mensaje":"Acceso autenticado a DigitalFix."}` |
| GET /api/catalog/services | 200 array `{id,nombre,descripcion,tarifa}` |
| GET /api/workorders | 200 array de órdenes propias |
| POST /api/workorders | 201 orden persistida |
| GET /api/workorders/{id} | 200 orden propia; 404 inexistente o ajena |

Solicitud pública de creación (Angular construye exactamente estos tres campos):

```json
{"servicioId":1,"descripcion":"Revisión eléctrica","direccion":"Calle 123"}
```

Respuesta de orden:

```json
{"id":1,"servicioId":1,"descripcion":"Revisión eléctrica","direccion":"Calle 123","solicitanteId":"oid-del-usuario","fechaCreacion":"2026-09-15T00:00:00Z","estado":"CREADA"}
```

IDs numéricos positivos, tarifa numérica, fecha ISO 8601 UTC. Descripción de orden:
1–1000 caracteres, dirección: 1–300 (sin solo espacios). `descripcion` de catálogo
puede ser null. BFF añade `solicitanteId` únicamente al POST interno; en GET interno
usa `?solicitanteId=<oid>`. El cliente no puede sobrescribirlo con body ni query.
Sin token/inválido: 401. Sin scope/rol: 403. Servicio inexistente: 400 sin crear orden.
Una orden ajena responde 404 para no revelar existencia. Caída de dominio: 502.

## 7. Datos y demostración real

Si no hay servicios, crear uno desde una sesión administrativa en EC2 dentro de
la red Docker. Esta operación escribe un dato real; no repetir si ya existe.
La imagen JDK ya se obtiene al construir el backend:

```bash
docker run --rm -i --network digitalfix_backend eclipse-temurin:21-jdk jshell - <<'JAVA'
import java.net.*;
import java.net.http.*;
var request = HttpRequest.newBuilder(URI.create("http://catalog:8081/api/catalog/services")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"nombre\":\"Mantención eléctrica\",\"descripcion\":\"Revisión\",\"tarifa\":25000}")).build();
var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.statusCode());
System.out.println(response.body());
/exit
JAVA
```

En el navegador local: iniciar sesión, abrir Catálogo, ir a Órdenes, elegir servicio,
crear y consultar. En DevTools/Network verificar destino Gateway, Bearer access token,
body sin solicitanteId y 201. No exportar HAR ni capturas con Authorization.

Desde el equipo local, Python 3 estándar permite comprobar estados y aislamiento:

```bash
python3 deploy/smoke.py
```

El script pide tokens de A/B y de un usuario con scope sin rol con entrada oculta,
no los guarda ni los imprime. Deben ser access tokens de Entra de la API, obtenidos
mediante MSAL; obtenerlos localmente de la solicitud en DevTools. Pedirá el oid real
de A y una pausa para ejecutar en EC2:

```bash
docker compose restart workorders
docker compose logs --tail 50 workorders
```

Después recupera exactamente la misma orden. Esta es la comprobación pendiente
de persistencia **Oracle RDS**, distinta de la prueba H2. Verificar también rechazo
con token válido destinado a otra audiencia (401) y token sin scope (403).
Cerrar sesión y probar login/logout con retorno a `http://localhost`.

## 8. Validación reproducible y límites

Desde el padre de los repositorios en PowerShell:

```powershell
Push-Location digitalfix-frontend
npm ci
npm test -- --watch=false
npm run build -- --configuration production
Pop-Location
foreach ($repo in 'digitalfix-ms-bff','digitalfix-ms-catalog','digitalfix-ms-workorders') {
  Push-Location $repo
  .\mvnw.cmd -B verify
  if ($LASTEXITCODE -ne 0) { throw "Falló $repo" }
  Pop-Location
}
```

Pruebas de dominio usan H2 y no necesitan Oracle/DB_* reales. Las pruebas del BFF
firman JWT con claves locales y llaman por HTTP a dobles de Catalog/Workorders.
Ver [VERIFICATION.md](VERIFICATION.md) para resultados de esta revisión y pendientes.
No se afirma que una compilación local certifique políticas remotas ni permisos del Lab.

## Referencias oficiales

- [JWT Authorizer](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html).
- [CORS y OPTIONS](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html).
- [Integración privada y path](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html).
- [Claims de Entra](https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference).
- [Versión del access token](https://learn.microsoft.com/en-us/entra/identity-platform/access-tokens).
