# DigitalFix — BFF

Backend for Frontend de DigitalFix.

Centraliza las solicitudes provenientes del frontend, valida la identidad y los permisos mediante JWT y coordina la comunicación con los microservicios de dominio.

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

## Responsabilidad del BFF

El BFF actúa como intermediario entre el frontend y los microservicios internos.

Flujo general:

```text
Frontend
    ↓
API Gateway
    ↓
BFF
    ├──→ Workorders
    └──→ Catalog
```

El frontend no debe comunicarse directamente con Workorders o Catalog.

## Seguridad

El BFF valida los access tokens emitidos por Microsoft Entra ID.

Se verifican:

- firma del JWT;
- issuer;
- audience;
- vigencia;
- scope `access_as_user`;
- roles de la aplicación.

Roles considerados actualmente:

```text
Admin
Operador
Cliente
```

La identidad del usuario se obtiene mediante el claim:

```text
oid
```

No se utiliza `sub` como fallback.

Si una operación de órdenes requiere identidad y el token no contiene un `oid` válido, el BFF responde:

```text
403 Forbidden
```

## Integración con Catalog

El BFF consulta Catalog para obtener los servicios técnicos disponibles.

Endpoint interno utilizado:

```http
GET /api/catalog/services
```

Antes de crear o actualizar una orden, el BFF comprueba que el servicio seleccionado exista en Catalog.

La URL del microservicio se configura mediante:

```text
CATALOG_URL
```

En desarrollo local se utiliza:

```text
http://localhost:8081
```

## Integración con Workorders

El BFF coordina las operaciones relacionadas con órdenes de trabajo.

Actualmente permite:

- crear órdenes;
- listar órdenes;
- consultar una orden;
- actualizar una orden;
- cambiar su estado;
- eliminar una orden cuando corresponde.

La URL del microservicio se configura mediante:

```text
WORKORDERS_URL
```

En desarrollo local se utiliza:

```text
http://localhost:8082
```

## Identidad de las órdenes

Para las operaciones del cliente, el BFF obtiene el identificador del usuario desde el JWT y lo envía internamente como:

```text
solicitanteId
```

El frontend no necesita proporcionar directamente este identificador.

Ejemplo del flujo:

```text
JWT
 ↓
BFF obtiene oid
 ↓
solicitanteId
 ↓
Workorders
```

Actualmente las consultas de órdenes están limitadas a las órdenes asociadas al solicitante autenticado.

## Repuestos asociados a órdenes

El BFF también transmite y recibe los repuestos asociados a una orden.

Cada repuesto se representa mediante:

```text
repuestoId
cantidad
```

Ejemplo de cambio de estado:

```json
{
  "status": "ASIGNADA",
  "tecnicoId": "tecnico-01",
  "repuestos": [
    {
      "repuestoId": 4,
      "cantidad": 2
    },
    {
      "repuestoId": 7,
      "cantidad": 1
    }
  ]
}
```

El contrato utilizado por el BFF incluye:

```text
status
tecnicoId
repuestos[]
```

Cada elemento de `repuestos` contiene:

```text
repuestoId
cantidad
```

## Validación de repuestos

Antes de enviar una solicitud hacia Workorders, el BFF valida que:

- `repuestoId` exista en la solicitud;
- `repuestoId` sea mayor que cero;
- `cantidad` exista;
- `cantidad` sea mayor que cero;
- los repuestos solo se envíen cuando el estado solicitado sea `ASIGNADA`.

Si la solicitud contiene datos inválidos, el BFF responde:

```text
400 Bad Request
```

El BFF no descuenta directamente el stock.

El flujo completo es:

```text
Frontend
    ↓
BFF
    ↓
Workorders
    ↓
Catalog
    ↓
descuento de stock
```

Workorders coordina el descuento con Catalog al asignar la orden.

## Respuesta de órdenes

El modelo de respuesta del BFF también contempla los repuestos almacenados en Workorders.

Una orden puede contener:

```text
id
servicioId
descripcion
direccion
solicitanteId
fechaCreacion
estado
tecnicoId
actualizadoPor
fechaActualizacion
repuestos[]
```

Esto permite que posteriormente el frontend pueda mostrar los repuestos asociados a una orden.

## Configuración

Las direcciones de los microservicios se configuran mediante:

```text
CATALOG_URL
WORKORDERS_URL
```

En desarrollo local los valores utilizados son:

```text
CATALOG_URL=http://localhost:8081
WORKORDERS_URL=http://localhost:8082
```

En ejecución mediante contenedores deben configurarse con las direcciones internas correspondientes.

Ejemplo:

```text
CATALOG_URL=http://catalog:8081
WORKORDERS_URL=http://workorders:8082
```

El BFF no utiliza una base de datos propia y no requiere credenciales Oracle.

## Construcción y pruebas

Ejecutar:

```powershell
.\mvnw.cmd -B verify
```

Las pruebas utilizan:

- JWT firmados localmente;
- servidores HTTP de dominio simulados;
- configuración de seguridad local para pruebas.

No requieren:

- credenciales reales de Entra ID;
- credenciales Oracle.

Se verificó con `mvnw verify` que la extensión del contrato de órdenes para incluir repuestos no rompe las pruebas automatizadas existentes.

También se comprobó que el BFF puede:

- recibir repuestos en una solicitud de cambio de estado;
- validar sus identificadores y cantidades;
- transmitirlos hacia Workorders;
- recibir los repuestos incluidos en la respuesta de una orden.

Las pruebas actuales no representan todavía una prueba HTTP completa entre:

```text
BFF
→ Workorders
→ Catalog
→ Oracle
```

Esa comprobación debe realizarse durante la prueba integrada del sistema.

## Despliegue

El BFF es el punto de entrada del backend después de API Gateway.

El flujo esperado es:

```text
Frontend
    ↓
API Gateway
    ↓
BFF
    ├──→ Workorders
    │       ↓
    │     Catalog
    │
    └──→ Catalog
```

Los microservicios de dominio deben mantenerse dentro de la infraestructura interna y no exponerse directamente a Internet.

La configuración definitiva de:

- puertos;
- direcciones internas;
- contenedores;
- API Gateway;
- CORS;
- JWT;
- conectividad entre servicios;

se administra y verifica junto con el repositorio:

```text
digitalfix-infra
```

El endpoint:

```http
GET /healthz
```

se utiliza para comprobación de salud del BFF y no contiene información de negocio.

No debe publicarse como una ruta de negocio en API Gateway.

## Documentación adicional

`DEPLOYMENT.md` contiene documentación histórica y técnica relacionada con despliegue, Entra ID, API Gateway y contratos.

`VERIFICATION.md` conserva registros de verificaciones realizadas anteriormente y no debe considerarse automáticamente como representación del estado actual del sistema.

## Flujo de trabajo

Los cambios deben integrarse mediante ramas y Pull Requests antes de incorporarse a `main`.