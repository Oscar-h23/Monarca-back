# Monarca Back · Spring Boot + Supabase MonarcaDB

Backend separado del [repositorio original Monarca](https://github.com/jpumaricradgjdgp-cyber/Monarca_herramientas_de_desarrollo).

**Stack:** Java 21, Spring Boot 3.4.2, Spring Security (JWT), Spring Data JPA y PostgreSQL 17.

## Base de datos

Proyecto conectado: **MonarcaDB** (Supabase, referencia `ldjuyocsuvvvbllgdclv`), región `us-east-1`.

- Esquema privado `monarca`: 10 tablas compatibles con las entidades JPA de la aplicación.
- El SQL reproducible está en [`db/schema/monarca_initial.sql`](db/schema/monarca_initial.sql). Se aplicó en MonarcaDB mediante una migración registrada `monarca_initial_schema`.
- Datos iniciales: roles `ADMIN`/`CLIENTE`, categorías `Tops`/`Bottoms`/`Calzado`/`Accesorios`, modalidad `Pago por coordinar`.
- No hay usuarios administradores, productos ni pedidos iniciales. No se crean contraseñas de demostración.
- El backend exige `spring.jpa.hibernate.ddl-auto=validate` y **no crea ni altera tablas al arrancar**.
- El esquema `monarca` no está expuesto por la API REST pública de Supabase; se revocaron permisos a `anon` y `authenticated`. El asesor de Supabase marca RLS deshabilitado en tablas privadas; si alguna vez se expone este esquema a Data API habrá que diseñar las políticas RLS previamente.

## Conectar Java a Supabase

1. Ve al proyecto [MonarcaDB](https://supabase.com/dashboard/project/ldjuyocsuvvvbllgdclv) y pulsa **Connect**.
2. Elige **Session pooler → JDBC** (puerto `5432`) y copia la URL. La conexión directa del `.env.example` usa IPv6; en redes sin IPv6 utiliza la del session pooler.
3. Copia `.env.example` a `.env` y reemplaza `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET`. Usa `sslmode=require`. No subas `.env` a Git.
4. Exporta las variables y arranca el servicio (Java 21):

```bash
cp .env.example .env
# Edita .env con los datos reales antes de continuar.
set -a
source .env
set +a
./mvnw spring-boot:run
```

Para crear una clave JWT aleatoria de más de 32 bytes, genera un valor único de forma segura con tu gestor de secretos o `openssl rand -base64 48`.

**Importante:** Hibernate usa prepared statements; la [guía de Supabase para Spring Boot](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot) desaconseja usar **Transaction pooler (6543)** como fuente de datos principal. Prefiere Session pooler (5432).

## Endpoints

- `GET /api/productos`, `GET /api/categorias`: catálogo público
- `POST /api/auth/registro`, `POST /api/auth/login`: cuentas
- `POST /api/auth/olvide-password`, `POST /api/auth/restablecer-password`: recuperación, si se configura correo
- `POST /api/pedidos/procesar`: registra un pedido autenticado
- `GET /api/pedidos`, altas, ediciones y borrados de productos: requieren `ROLE_ADMIN`

No se procesa un cobro real: el método **Pago por coordinar** solo genera un pedido con estado pendiente. Registra al primer usuario mediante el formulario antes de asignarle el rol de administrador desde un canal privilegiado y verificado.

## Pruebas y ejecución

```bash
./mvnw test
./mvnw spring-boot:run
```

La CI compila y ejecuta tests unitarios seleccionados. No hay todavía pruebas del backend conectado a PostgreSQL desde CI; para completar el despliegue se deben ejecutar pruebas de integración y revisar CORS, seguridad y backups.
