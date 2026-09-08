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

## Flujo de trabajo

Los cambios se realizan en una rama de trabajo y se integran a `main`
mediante un Pull Request revisado y aprobado por otro integrante.