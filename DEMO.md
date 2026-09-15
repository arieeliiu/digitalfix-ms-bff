# Demo mínima: catálogo y órdenes con JWT

## Alcance implementado

Angular solicita un access token de DigitalFix API mediante MSAL. HTTP API Gateway
valida issuer, audience y scope; Spring Security en el BFF vuelve a validar el JWT
y exige access_as_user más uno de los roles Admin, Operador o Cliente.

El BFF consulta Catalog, verifica el servicio elegido y crea la orden en Workorders.
El campo solicitanteId se obtiene del claim sub del JWT validado, nunca del cuerpo.
La consulta por ID verifica propiedad y el listado filtra por ese mismo identificador.
En esta demo TODOS los roles consultan únicamente sus propias órdenes.
El JWT se reenvía en Authorization a los servicios internos; Catalog y Workorders
todavía NO lo validan. Solo el BFF debe tener acceso de entrada público controlado;
los servicios de dominio deben permanecer sin puertos publicados, en la red Docker.
La validación JWT independiente en cada microservicio queda fuera de este incremento.

## Configuración del BFF en Docker Compose

Agregar a environment del servicio bff, sustituyendo nombres por los servicios
reales del compose (este fragmento asume bff, catalog y workorders):

```yaml
services:
  bff:
    environment:
      CATALOG_URL: http://catalog:8080
      WORKORDERS_URL: http://workorders:8082
```

Los tres contenedores deben compartir red. En Docker, localhost dentro del BFF
apunta al propio BFF, no a Catalog. El puerto interno de Catalog actual es 8080.
Para ejecutar fuera de Docker, el BFF usa por defecto Catalog localhost:8081 y
Workorders localhost:8082; iniciar Catalog con SERVER_PORT=8081.

Reconstruir/desplegar las imágenes del BFF y Workorders desde estos cambios.
Catalog usa sus endpoints existentes y no requiere cambios de código.
Actualizar Angular y reconstruir/desplegar el frontend después de configurar
apiGatewayUrl con la Invoke URL real (incluye stage cuando corresponda).
Si el frontend está desplegado, configurar también sus redirectUri de MSAL y el
registro SPA en Entra para el origen real, en vez de localhost:4200.

## Rutas de AWS API Gateway HTTP API

Crear/asociar estas rutas a integraciones del BFF, no directamente a los dominios:

| Método | Ruta pública | Ruta de destino en BFF |
|---|---|---|
| GET | /api/perfil | /api/perfil |
| GET | /api/catalog/services | /api/catalog/services |
| GET | /api/workorders | /api/workorders |
| POST | /api/workorders | /api/workorders |
| GET | /api/workorders/{id} | /api/workorders/{id} |

Para integración HTTP, usar la URL real del BFF más la ruta de destino.
En el endpoint parametrizado, conservar literalmente {id} en la URL de integración.
Para un backend privado usar VPC Link; si el stage se añade al destino,
configurar overwrite:path = $request.path. No enviar tokens por HTTP público:
usar una URL HTTPS del backend o integración privada.

Adjuntar el autorizador JWT a TODAS las rutas anteriores:

- Issuer: https://login.microsoftonline.com/762b016c-dc33-4db0-ad42-44f32afe71f4/v2.0
- Audience: 85329d90-58f8-4317-a820-452599b3b04c
- Identity source: $request.header.Authorization
- Scope requerido por ruta: access_as_user

CORS: origen exacto de Angular; métodos GET, POST, OPTIONS; headers Authorization
y Content-Type. El preflight OPTIONS no requiere JWT. Guardar/desplegar el stage.

## Preparación de los datos

Debe existir al menos un servicio en Catalog. Si la base está vacía, un administrador
puede usar el endpoint interno existente POST /api/catalog/services desde la red
Docker, con {"nombre":"Mantención eléctrica","descripcion":"Revisión","tarifa":25000}.
No se agregó creación pública de catálogo al BFF en este incremento.

## Demostración desde el navegador

1. Iniciar sesión con una cuenta cuyo access token tenga Admin, Operador o Cliente.
2. Abrir Catálogo: Network muestra GET /api/catalog/services con Bearer y HTTP 200.
3. Abrir Órdenes: se consulta el catálogo y GET /api/workorders.
4. Elegir un servicio, completar descripción y dirección y pulsar Crear orden.
5. POST /api/workorders lleva solo servicioId, descripcion y direccion; responde 201
   con id, solicitanteId, fechaCreacion y estado CREADA generados por el backend.
6. Pulsar Consultar: GET /api/workorders/{id} devuelve 200 y el detalle persistido.
7. Recargar la página (y, para demostrar persistencia, reiniciar Workorders): la
   misma orden debe seguir disponible desde Oracle.
8. Con una segunda cuenta, esa orden no aparece y su consulta por ID responde 404.
9. Sin token o con firma inválida: 401. Token sin scope/rol necesario: rechazo.
10. Enviar servicioId inexistente: 400 y ninguna nueva orden.

Un 200 de /api/perfil solo demuestra Gateway→BFF. La respuesta de catálogo y la
creación/recuperación de la orden demuestran las llamadas a los servicios.
Los KPIs del dashboard siguen identificados como ejemplos y no forman parte de la demo.
Órdenes antiguas con solicitanteId de pruebas no se asignan automáticamente a usuarios reales.

## Verificación local

- BFF: .\mvnw.cmd test (JWT firmados, clientes HTTP reales contra un servidor de prueba,
  identidad, propiedad, roles, servicio inexistente y errores downstream).
- Workorders: .\mvnw.cmd -Dtest=OrdenTrabajoControladorTests test (persistencia simulada,
  validación y filtro por solicitante). La prueba de contexto con Oracle requiere DB_*.
- Frontend: npm test -- --watch=false y npm run build.

Estas pruebas no certifican AWS, Entra real ni persistencia Oracle. Eso se verifica
con los pasos de demostración después de desplegar y configurar las URLs reales.
