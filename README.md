# DigitalFix — BFF

Backend for Frontend de DigitalFix.

Centraliza las solicitudes provenientes del frontend, valida identidad y permisos mediante JWT y coordina la comunicación con los microservicios internos.

## Integrantes

- Ariel Molina
- Lucas Ferrada

## Tecnologías

- Java 21
- Spring Boot 4.1.1
- Spring Security
- OAuth2 Resource Server
- RestClient
- Microsoft Entra ID
- Maven
- Docker

## Arquitectura

El BFF es el punto de entrada del backend después de AWS API Gateway.

El flujo actual es:

```text
Angular
   ↓
AWS API Gateway
   ↓
BFF
   ├──→ Catalog
   └──→ Workorders
            ↓
          Catalog
   ↓
Oracle RDS
```

El frontend no se comunica directamente con Catalog ni Workorders.

## Responsabilidades

El BFF se encarga de:

- validar los JWT recibidos desde el frontend;
- verificar scope y roles;
- obtener la identidad del usuario autenticado;
- aplicar reglas de autorización;
- filtrar órdenes según el rol;
- validar datos antes de enviarlos a los microservicios;
- reenviar solicitudes hacia Catalog y Workorders;
- propagar el Bearer token hacia los microservicios internos;
- ocultar al frontend las direcciones internas de los servicios.

El BFF no utiliza una base de datos propia.

## Seguridad

El BFF funciona como OAuth2 Resource Server.

Valida access tokens emitidos por Microsoft Entra ID.

Se verifican:

- firma del JWT;
- issuer;
- audience;
- vigencia;
- scope `access_as_user`;
- roles de la aplicación.

El issuer configurado corresponde al tenant de DigitalFix y la audience corresponde a DigitalFix API.

## Roles

Los roles utilizados actualmente son:

```text
Admin
Operador
Cliente
```

### Admin

Puede:

- consultar todas las órdenes;
- crear órdenes;
- actualizar órdenes;
- cambiar estados;
- eliminar órdenes cuando las reglas de dominio lo permiten;
- consultar servicios;
- consultar repuestos;
- crear servicios;
- actualizar servicios;
- eliminar servicios;
- crear repuestos;
- actualizar repuestos;
- eliminar repuestos.

### Operador

Puede:

- consultar todas las órdenes;
- crear órdenes;
- actualizar órdenes;
- cambiar estados;
- eliminar órdenes cuando corresponde;
- consultar servicios;
- consultar repuestos.

No puede realizar operaciones administrativas de escritura sobre el catálogo.

### Cliente

Puede:

- consultar sus propias órdenes;
- crear órdenes;
- consultar una orden propia;
- actualizar sus propias órdenes;
- eliminar sus propias órdenes cuando las reglas de dominio lo permiten;
- consultar servicios.

No puede:

- cambiar estados administrativos;
- consultar repuestos;
- modificar el catálogo;
- acceder a órdenes pertenecientes a otros usuarios.

## Identidad del usuario

La identidad se obtiene desde el claim:

```text
oid
```

del JWT.

El BFF utiliza este valor como:

```text
solicitanteId
```

para las operaciones relacionadas con órdenes.

El frontend no debe enviar manualmente el identificador del usuario autenticado.

Flujo:

```text
JWT
 ↓
BFF obtiene oid
 ↓
solicitanteId
 ↓
Workorders
```

Si una operación requiere identidad y el token no contiene un `oid` válido, el BFF responde:

```text
403 Forbidden
```

## Endpoint de perfil

Permite verificar que el usuario está autenticado correctamente.

```http
GET /api/perfil
```

Requiere:

```text
scope access_as_user
```

## Endpoints de órdenes

El BFF expone:

| Método | Ruta | Función |
|---|---|---|
| GET | `/api/workorders` | Lista órdenes |
| GET | `/api/workorders/{id}` | Consulta una orden |
| POST | `/api/workorders` | Crea una orden |
| PUT | `/api/workorders/{id}` | Actualiza una orden |
| PUT | `/api/workorders/{id}/status` | Cambia el estado |
| DELETE | `/api/workorders/{id}` | Elimina una orden cuando corresponde |

## Creación de órdenes

El frontend envía:

```json
{
  "servicioId": 1,
  "descripcion": "Revisión de instalación eléctrica",
  "direccion": "Calle de prueba 123"
}
```

El BFF agrega internamente la identidad obtenida desde el JWT antes de comunicarse con Workorders.

Ejemplo conceptual:

```text
Frontend
envía:
servicioId
descripcion
direccion

BFF agrega:
solicitanteId = oid del JWT
```

Antes de crear o actualizar una orden, el BFF comprueba que el servicio seleccionado exista en Catalog.

## Consulta de órdenes por rol

Para `Admin` y `Operador`, el BFF solicita la lista global de órdenes.

Para `Cliente`, el BFF utiliza su `oid` como `solicitanteId` y devuelve únicamente las órdenes que pertenecen al usuario autenticado.

Si un cliente intenta consultar una orden ajena, el BFF responde como orden no encontrada.

## Estados de órdenes

Los estados reconocidos por el BFF son:

```text
CREADA
ASIGNADA
EN_DESPLAZAMIENTO
EN_EJECUCION
CERRADA
CANCELADA
```

El cambio de estado se realiza mediante:

```http
PUT /api/workorders/{id}/status
```

La autorización para cambiar estados corresponde a:

```text
Admin
Operador
```

Las reglas definitivas de transición son aplicadas por Workorders.

## Repuestos asociados a órdenes

Durante la asignación de una orden se pueden enviar repuestos.

Ejemplo:

```json
{
  "status": "ASIGNADA",
  "tecnicoId": "tecnico-01",
  "repuestos": [
    {
      "repuestoId": 4,
      "cantidad": 2
    }
  ]
}
```

Cada repuesto debe contener:

```text
repuestoId
cantidad
```

El BFF valida que:

- `repuestoId` exista;
- `repuestoId` sea mayor que cero;
- `cantidad` exista;
- `cantidad` sea mayor que cero;
- los repuestos solo se envíen cuando el estado solicitado sea `ASIGNADA`.

Si los datos son inválidos, responde:

```text
400 Bad Request
```

El BFF no descuenta directamente stock.

El flujo es:

```text
Frontend
   ↓
BFF
   ↓
Workorders
   ↓
Catalog
   ↓
Oracle RDS
```

Workorders coordina el descuento con Catalog.

## Catálogo

El BFF expone operaciones para servicios y repuestos.

### Servicios

| Método | Ruta | Rol |
|---|---|---|
| GET | `/api/catalog/services` | Admin, Operador, Cliente |
| POST | `/api/catalog/services` | Admin |
| PUT | `/api/catalog/services/{id}` | Admin |
| DELETE | `/api/catalog/services/{id}` | Admin |

### Repuestos

| Método | Ruta | Rol |
|---|---|---|
| GET | `/api/catalog/spare-parts` | Admin, Operador |
| POST | `/api/catalog/spare-parts` | Admin |
| PUT | `/api/catalog/spare-parts/{id}` | Admin |
| DELETE | `/api/catalog/spare-parts/{id}` | Admin |

El BFF reenvía estas solicitudes hacia Catalog utilizando el mismo Bearer token recibido desde el frontend.

## Endpoint interno de descuento de stock

Catalog posee internamente:

```http
POST /api/catalog/spare-parts/discount-stock
```

Este endpoint no está disponible como operación pública a través del BFF.

El BFF bloquea explícitamente el acceso externo a esa ruta.

El descuento debe ocurrir únicamente mediante:

```text
Workorders
→ Catalog
```

## Comunicación con Catalog

La dirección de Catalog se configura mediante:

```text
digitalfix.catalog-url
```

En Docker se utiliza:

```text
http://catalog:8081
```

También puede sobrescribirse mediante la variable:

```text
DIGITALFIX_CATALOG_URL
```

En ejecución local puede utilizarse:

```text
http://localhost:8081
```

## Comunicación con Workorders

La dirección de Workorders se configura mediante:

```text
digitalfix.workorders-url
```

En Docker se utiliza:

```text
http://workorders:8082
```

También puede sobrescribirse mediante:

```text
DIGITALFIX_WORKORDERS_URL
```

En ejecución local puede utilizarse:

```text
http://localhost:8082
```

## Propagación del JWT

El BFF reenvía el access token hacia los microservicios internos:

```http
Authorization: Bearer <token>
```

Por lo tanto, el flujo de identidad se mantiene durante la comunicación:

```text
Frontend
→ API Gateway
→ BFF
→ Catalog / Workorders
```

## Health check

El BFF expone:

```http
GET /healthz
```

Esta ruta es pública y se utiliza únicamente para comprobar que el servicio está activo.

No contiene información de negocio.

## Configuración

La configuración principal se encuentra en:

```text
src/main/resources/application.properties
```

Incluye:

- URL de Catalog;
- URL de Workorders;
- issuer de Microsoft Entra ID;
- audience esperada del JWT.

El BFF no necesita:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

porque no posee persistencia propia.

## Ejecución local

En Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

El servicio utiliza por defecto:

```text
http://localhost:8080
```

Para ejecución local, Catalog y Workorders deben estar disponibles en sus respectivas direcciones configuradas.

## Construcción y pruebas

Ejecutar:

```powershell
.\mvnw.cmd -B verify
```

Las pruebas cubren principalmente:

- configuración de seguridad;
- JWT;
- scopes;
- roles;
- autorización de endpoints;
- contratos de órdenes;
- validación de solicitudes;
- comunicación simulada con los servicios de dominio.

Las pruebas automatizadas no reemplazan la prueba integrada real entre:

```text
Frontend
→ API Gateway
→ BFF
→ Workorders / Catalog
→ Oracle RDS
```

Ese flujo también se verifica durante las pruebas funcionales del sistema.

## Docker

El BFF se construye mediante su `Dockerfile`.

El despliegue conjunto se administra desde:

```text
digitalfix-infra/docker-compose.yml
```

El Compose central inicia:

```text
BFF
Catalog
Workorders
```

Solo el BFF publica su puerto hacia el host.

Catalog y Workorders permanecen como servicios internos dentro de la red Docker.

## Despliegue actual

El flujo desplegado utilizado por DigitalFix es:

```text
Angular
   ↓
AWS API Gateway
   ↓
BFF en EC2
   ├──→ Catalog
   └──→ Workorders
            ↓
          Catalog
   ↓
Oracle Database en Amazon RDS
```

API Gateway funciona como punto de entrada externo.

El BFF mantiene la validación de JWT, scopes y roles antes de ejecutar las operaciones de negocio.

## Manejo de errores

El BFF utiliza respuestas HTTP para representar los principales errores:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
502 Bad Gateway
503 Service Unavailable
```

Los errores provenientes de los microservicios son traducidos o propagados según la operación correspondiente.

## Estructura principal

El proyecto está organizado por responsabilidad:

```text
controller
service
client
dto
config
exception
```

Los controllers reciben las solicitudes HTTP.

Los services aplican reglas y coordinación.

Los clients realizan la comunicación con Catalog y Workorders.

Los DTOs representan solicitudes y respuestas.

La configuración de seguridad y clientes se mantiene dentro de `config`.

## Estado actual

Actualmente se encuentra funcionando la integración:

```text
Frontend
→ API Gateway
→ BFF
→ Catalog / Workorders
→ Oracle RDS
```

Se encuentran operativos:

- validación JWT con Microsoft Entra ID;
- autorización mediante scope y roles;
- identificación mediante `oid`;
- filtrado de órdenes del cliente;
- CRUD de órdenes;
- cambios de estado;
- asociación de repuestos;
- CRUD de servicios;
- CRUD de repuestos;
- comunicación BFF → Catalog;
- comunicación BFF → Workorders;
- propagación del Bearer token;
- integración con el despliegue Docker en EC2.

## Documentación adicional

Los archivos:

```text
DEPLOYMENT.md
VERIFICATION.md
REFACTOR.md
REFACTOR_LOMBOK.md
```

conservan información técnica e histórica del desarrollo.

El `README.md` representa el estado funcional actual del BFF.

## Flujo de trabajo

## Evaluación 2: contactos de órdenes

Validación de correos obligatorios: `./mvnw.cmd -B verify`, 75 pruebas aprobadas
y compilación correcta, con servicios HTTP simulados.

### Ajuste de edición y permisos (19/10/2026)

PUT de órdenes permite editar CREADA; DELETE permite CREADA/CANCELADA. Solo Admin
puede operar sobre órdenes ajenas. Cliente y Operador conservan el requisito de
propiedad. BFF valida el `oid` y el rol del JWT y reenvía el Bearer a Workorders,
sin parámetros de identidad para estas dos operaciones. Workorders vuelve a autorizar
y verifica el estado bajo bloqueo transaccional. No se cambian permisos de contactos
ni transiciones. Actualizar Workorders antes de esta versión del BFF: el contrato
anterior de Workorders exigía el parámetro `solicitanteId` en PUT/DELETE.

Validación del ajuste: `./mvnw.cmd -B verify`, 74 pruebas aprobadas. Incluye JWT
firmados localmente, intentos de suplantación por parámetros, permisos y estados.

La creación exige `emailContacto` y la transición CREADA → ASIGNADA exige `emailTecnico`,
ambos con formato de correo y máximo 254 caracteres. PUT sigue modificando solo
datos generales y no exige correo. Las respuestas
conservan ambos campos, que pueden ser `null`. La identidad sigue siendo el `oid`
del JWT; un identificador enviado por el cliente no sustituye al usuario autenticado.

`PATCH /api/workorders/{id}/contactos` reenvía el Bearer token a Workorders.
Requiere scope `access_as_user` y rol Cliente, Operador o Admin. El propietario
puede modificar `emailContacto`; Admin/Operador pueden modificar ambos correos.
Las propiedades omitidas mantienen el valor, incluso en órdenes antiguas sin correo.
Un `null` explícito se rechaza. No se permite vaciar el correo del cliente ni el
del técnico cuando la orden tiene técnico asignado.
También sirve para órdenes cerradas: no cambia estado ni stock y no reenvía
automáticamente comandos que antes se omitieron por falta de destinatario.

Validación local del Paso 6: `./mvnw.cmd -B verify`, 65 pruebas aprobadas,
incluyendo JWT firmados localmente y servidores HTTP simulados. Esto no certifica
Oracle, Entra, SMTP ni AWS reales. API Gateway debe permitir la ruta PATCH y CORS;
ver las instrucciones de integración en `digitalfix-infra` antes del despliegue.

Los cambios se desarrollan en ramas y se integran a `main` mediante Pull Request.

La rama `main` representa la versión estable utilizada para la entrega.
