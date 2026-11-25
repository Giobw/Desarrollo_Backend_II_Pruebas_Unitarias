# Pruebas unitarias e integración (JUnit 5 + Mockito + JaCoCo)

## Cómo ejecutar
```
.\mvnw.cmd test
```
No requiere MySQL ni Docker: el perfil `test` usa una base H2 en memoria (`src/test/resources/application-test.properties`).

## Reportes
- Resultados por clase (XML y TXT): `target/surefire-reports/`
- Cobertura de código (HTML): `target/site/jacoco/index.html`

## Organización
| Paquete de prueba | Qué valida |
|---|---|
| `service` | Reglas de negocio de Cita, ExpedienteMedico, Mascota, Venta y Usuario, con repositorios simulados (Mockito) |
| `entity` | Construcción de las entidades, relaciones entre ellas y consistencia de datos |
| `security` | JwtUtil, UsuarioDetails, SeguridadService, LoginAttemptService y, de punta a punta, login, JWT y autorización por rol (MockMvc + H2) |

## Convención de nombres
`metodo_escenario_resultadoEsperado`, por ejemplo `agendarCita_fechaPasada_lanzaExcepcion`.
