# Monarca Back — Spring Boot

Backend extraído de `backend/` del [repositorio original](https://github.com/jpumaricradgjdgp-cyber/Monarca_herramientas_de_desarrollo).

**Stack:** Java 21, Spring Boot 3.4.2, Spring Security JWT, Spring Data JPA y PostgreSQL.

## Configuración

Configura las variables de entorno `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (clave JWT apropiadamente larga). La base debe existir y contar con el esquema `monarca` y sus tablas; la aplicación utiliza `ddl-auto=validate` y **no crea el esquema automáticamente**. Para recuperación de contraseña también configurar `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` y `PASSWORD_RESET_URL`.

## Ejecución local

```bash
./mvnw spring-boot:run
```

Pruebas: `./mvnw test`. API por defecto en `http://localhost:8080/api`.

## Estado de la migración

Los 55 archivos del backend original, incluido Maven Wrapper, clases y pruebas, fueron trasladados sin modificar su implementación en esta primera migración. No se trasladaron registros ni credenciales de la base de datos. Revisar configuración, políticas CORS, seguridad y pruebas de integración antes de producción.
