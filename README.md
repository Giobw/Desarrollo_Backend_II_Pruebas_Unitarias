# Backend Clínica PetSalud: seguridad y pruebas unitarias

Backend Spring Boot 3.3 / Java 17 / MySQL con autenticación JWT, autorización por roles y una batería de pruebas
automatizadas (JUnit 5, Mockito y JaCoCo).

- **Asignatura:** Desarrollo Backend II (PBY2202)
- **Actividad:** Integrando, aplicando y analizando resultados de pruebas unitarias en microservicios con JUnit
- **Autor:** Giovanni Bencini

## Contenido

1. [Roles](#roles)
2. [Requisitos y ejecución](#requisitos-y-ejecución)
3. [Uso de la API](#uso-de-la-api)
4. [Medidas de seguridad](#medidas-de-seguridad)
5. [Pruebas unitarias y de integración](#pruebas-unitarias-y-de-integración)
6. [Estructura del proyecto](#estructura-del-proyecto)

## Roles

| Rol | Alcance |
|---|---|
| CLIENTE | Sus propios datos, mascotas, citas, expedientes, notificaciones y recompensas |
| EMPLEADO | Operación diaria (actúa como veterinario): citas, expedientes, inventario y notificaciones |
| GERENTE | Todo lo anterior, más gestión de usuarios, eliminaciones y ventas globales |

## Requisitos y ejecución

Requisitos: Java 17 o superior, Docker Desktop (solo para ejecutar la aplicación) y Postman (opcional).

1. Levantar MySQL con Docker (el ejemplo publica el puerto 3307 porque el 3306 puede estar ocupado):
   ```
   docker run --name petsalud-mysql -e MYSQL_ROOT_PASSWORD=root123 -e MYSQL_DATABASE=petsalud_db -p 3307:3306 -d mysql:8
   ```
2. Revisar en `src/main/resources/application.properties` que el puerto de `spring.datasource.url` coincida con el
   del contenedor (3307 en el ejemplo). La opción `allowPublicKeyRetrieval=true` es necesaria en local con MySQL 8 sin SSL.
3. Definir las variables de entorno y ejecutar (PowerShell):
   ```
   $env:DB_PASSWORD="root123"
   .\mvnw.cmd spring-boot:run
   ```
   Variables disponibles: `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (Base64, mínimo 256 bits) y `JWT_EXPIRATION_MS`.
   Hay un secreto por defecto solo para desarrollo.

Usuarios de demostración (solo desarrollo, `app.seed-demo-users`):

| Correo | Contraseña | Rol |
|---|---|---|
| gerente@petsalud.cl | Gerente123! | GERENTE |
| empleado@petsalud.cl | Empleado123! | EMPLEADO |
| cliente@petsalud.cl | Cliente123! | CLIENTE |

## Uso de la API

- `POST /api/auth/registro`: crea un usuario CLIENTE (el rol nunca se toma del cuerpo de la solicitud).
- `POST /api/auth/login`: devuelve `{ token, tipo: "Bearer", expiraEnMs, email, rol }`.
- Resto de la API (`/api/usuarios`, `/api/mascotas`, `/api/citas`, `/api/expedientes`, `/api/productos`,
  `/api/ventas`, `/api/notificaciones`, `/api/recompensas`): requiere el header `Authorization: Bearer <token>`.

En `docs/` hay una colección de Postman con pruebas de autenticación, roles, token alterado, SQL Injection, XSS y CSRF.

## Medidas de seguridad

- Contraseñas con BCrypt; JWT firmado con HS256, secreto externo y expiración de 1 hora.
- Sesión sin estado (STATELESS) y filtro que valida el token en cada solicitud.
- `@PreAuthorize` en todos los endpoints, más reglas de propiedad para que un cliente solo acceda a sus datos.
- Bloqueo temporal tras 5 intentos de login fallidos y registro de auditoría (`SEGURIDAD_AUDITORIA`).
- Validación de entrada, escape de HTML contra XSS (conserva tildes), consultas parametrizadas con JPA
  y cabeceras CSP, X-Frame-Options y nosniff.
- CSRF deshabilitado de forma deliberada: la identidad viaja en el header `Authorization`, no en cookies.

## Pruebas unitarias y de integración

Para ejecutarlas **no hace falta MySQL ni Docker**: el perfil `test` usa una base H2 en memoria.

```
.\mvnw.cmd test
```

- Resultados por clase: `target/surefire-reports/`
- Cobertura (HTML): `target/site/jacoco/index.html`

| Resumen | Valor |
|---|---|
| Pruebas | 135, todas aprobadas |
| Cobertura total (instrucciones) | 75 % |
| Paquete `service` | 80 % (Cita, Venta, Mascota, Expediente y Usuario al 100 % de instrucciones) |
| Paquete `security` | 97 % |

Qué valida cada paquete de pruebas:

| Paquete | Pruebas |
|---|---|
| `service` | Reglas de negocio de Cita, ExpedienteMedico, Mascota, Venta y Usuario con repositorios simulados (Mockito) |
| `entity` | Construcción de las entidades, relaciones y consistencia de datos |
| `security` | JwtUtil, UsuarioDetails, SeguridadService, LoginAttemptService y, de punta a punta, login, JWT y autorización por rol (MockMvc + H2) |

El detalle del diseño de las pruebas, la convención de nombres y cómo leer los reportes está en
[`docs/PRUEBAS.md`](docs/PRUEBAS.md).

## Estructura del proyecto

```
src/main/java/com/petsalud/backendpetsalud
├── config        Usuarios de demostración
├── controller    Endpoints REST protegidos con @PreAuthorize
├── dto           Objetos de entrada y salida (login, registro, usuario)
├── entity        Entidades JPA
├── exception     Manejo centralizado de errores
├── repository    Repositorios Spring Data
├── security      JWT, filtro, reglas de propiedad y monitoreo de intentos
└── service       Lógica de negocio
src/test          Pruebas unitarias y de integración (JUnit 5, Mockito, MockMvc)
docs              Colección de Postman y guía de pruebas
```
