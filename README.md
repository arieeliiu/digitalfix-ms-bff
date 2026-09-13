# digitalfix-ms-bff

BFF (Backend for Frontend) de DigitalFix. Su propósito es actuar como
intermediario entre el frontend y los microservicios, validar los JWT
y aplicar autorización.

## Integrantes

- Ariel Molina
- Lucas Ferrada

## Estado actual

Estructura inicial generada con Spring Initializr.
El proyecto compila, supera la prueba inicial y arranca localmente.

Pendiente, en tareas independientes:
- Validación de JWT y autorización.
- Comunicación con los microservicios.
- Implementación de endpoints del BFF.

## Tecnologías

- Java 21.
- Spring Boot 4.1.1.
- Spring Web.
- Maven y Maven Wrapper.

## Requisitos

- JDK 21 instalado.
- Acceso a internet para descargar dependencias en la primera ejecución.

No es necesario instalar Maven por separado: el repositorio incluye
Maven Wrapper.

## Ejecución local en Windows

Abre PowerShell en la raíz del repositorio, donde está `pom.xml`.

Comprueba que Java y el compilador estén disponibles:

```powershell
java -version
javac -version
```

Compila y ejecuta las pruebas:

```powershell
.\mvnw.cmd clean test
```

Inicia la aplicación:

```powershell
.\mvnw.cmd spring-boot:run
```

La aplicación escucha por defecto en http://localhost:8080.

Actualmente no hay un endpoint definido para `/`, por lo que visitar
esa dirección devuelve HTTP 404. Esto es esperado en esta etapa.

Para detener la aplicación, presiona Ctrl + C en la terminal.

## Configuración

La configuración compartida está en:

`src/main/resources/application.properties`

En esta etapa no se requieren credenciales ni variables de entorno
adicionales. No se deben guardar contraseñas, tokens ni secretos en Git.

## Identidad de la API en Microsoft Entra ID

Se reutiliza el tenant DigitalFix. El registro DigitalFix API admite
cuentas de esta organización y no tiene URI de redirección, ya que
el inicio de sesión se realizará desde el frontend.

Configuración verificada en el portal el 11 de septiembre de 2026:

| Dato | Valor |
|---|---|
| Tenant ID | `762b016c-dc33-4db0-ad42-44f32afe71f4` |
| Nombre del registro de la API | `DigitalFix API` |
| Client ID de la API | `85329d90-58f8-4317-a820-452599b3b04c` |
| URI de identificador de la API | `api://85329d90-58f8-4317-a820-452599b3b04c` |
| Scope delegado | `access_as_user` |
| Estado del scope | Habilitado |
| Quién puede dar consentimiento | Solo administradores |
| Nombre del registro del frontend | `DigitalFix Frontend` |
| Client ID del frontend | `0a57e0f7-1a4f-40f2-9011-eac64a4c49c6` |

Scope completo que deberá solicitar el frontend:

```text
api://85329d90-58f8-4317-a820-452599b3b04c/access_as_user
```

En DigitalFix Frontend se agregó este permiso delegado y se concedió
el consentimiento de administrador para DigitalFix. Permite solicitar
acceso a la API en nombre del usuario que inició sesión. La opción
"Solo administradores" determina quién concede el consentimiento;
los permisos de negocio de cada usuario se definirán mediante roles.

El frontend conserva además `User.Read` de Microsoft Graph, procedente
de la actividad anterior. Ese permiso no concede acceso a DigitalFix API.

Estos identificadores documentan la configuración de Entra y no son
secretos. El URI de identificador de la API tampoco es su URL de despliegue.

### Pendiente de integración

- Configurar MSAL en DigitalFix Frontend para solicitar el scope de la API.
- Validar el JWT y el scope en el BFF con Spring Security.
- Configurar los roles Admin, Operador y Cliente y sus asignaciones.
- Aplicar autorización por rol en los endpoints.
- Configurar API Gateway y verificar el flujo completo con tokens.

El registro y el consentimiento en Entra no implementan por sí solos
la seguridad del BFF. Esta tarea no modifica el código Java ni demuestra
todavía llamadas autenticadas desde DigitalFix Frontend.

## Flujo de trabajo

Los cambios se realizan en una rama de trabajo y se integran a `main`
mediante un Pull Request revisado y aprobado por otro integrante.

## Seguridad JWT

El BFF recibe access tokens mediante `Authorization: Bearer <token>`.

Spring Security valida firma, emisor, audiencia y vigencia del JWT.
Se utilizan tokens v2 de Microsoft Entra ID destinados a DigitalFix API.

- `/api/perfil`: requiere el scope `access_as_user`.
- `/api/administracion`: requiere el scope `access_as_user` y el rol `Admin`.
- Los roles de Entra se convierten en autoridades con prefijo `ROLE_`.
- Las solicitudes sin token o con token inválido reciben 401.
- Los tokens válidos sin permisos suficientes reciben 403.

Estos endpoints permiten comprobar la seguridad; todavía no implementan
operaciones de negocio ni comunicación con microservicios.

### Verificación

Ejecutar:

`.\mvnw.cmd verify`

Las pruebas automatizadas comprueban firma, emisor, audiencia,
expiración, vigencia futura, scopes y roles mediante tokens de prueba.

También se verificó manualmente el acceso a ambos endpoints con un
token real de Entra de un usuario Admin. Los casos Operador y Cliente
se comprobaron con tokens de prueba, no con cuentas reales.

API Gateway, integración con Angular y despliegue en EC2 se abordarán
en tareas posteriores.