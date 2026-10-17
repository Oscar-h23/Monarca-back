# Monarca Back · Spring Boot + Supabase MonarcaDB

Backend separado del [repositorio original Monarca](https://github.com/jpumaricradgjdgp-cyber/Monarca_herramientas_de_desarrollo).

**Stack:** Java 21, Spring Boot 3.4.2, Spring Security (JWT), Spring Data JPA y PostgreSQL 17.

## Base de datos

Proyecto conectado: **MonarcaDB** (Supabase, referencia `ldjuyocsuvvvbllgdclv`), región `us-east-1`.

- Esquema privado `monarca`: tablas compatibles con las entidades JPA, incluida la tabla de fotografías del probador.
- El SQL reproducible está en [`db/schema/monarca_initial.sql`](db/schema/monarca_initial.sql). Se aplicó en MonarcaDB mediante una migración registrada `monarca_initial_schema`.
- Datos iniciales: roles `ADMIN`/`CLIENTE`, categorías `Tops`/`Bottoms`/`Calzado`/`Accesorios`, modalidad `Pago por coordinar`.
- Las cuentas, productos y pedidos se gestionan desde la aplicación; ningún usuario ni contraseña se crea automáticamente con el esquema.
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

## Mi perfil y probador virtual (demostración)

- La migración aditiva [`db/migrations/20261017_perfil_probador.sql`](db/migrations/20261017_perfil_probador.sql) añade datos de entrega al usuario y la tabla privada `monarca.fotos_probador`. Ya fue aplicada al proyecto MonarcaDB; ejecútala en cualquier otra base de pruebas antes de arrancar con `ddl-auto=validate`.
- El usuario autenticado administra solo sus datos mediante `GET/PUT /api/perfil` y su imagen mediante `POST/GET/DELETE /api/perfil/foto`. Imagen JPG/PNG/WebP con un máximo de 4 MB. Se almacena en PostgreSQL en un esquema privado y solo se lee a través del JWT del usuario.
- `POST /api/probador/{idProducto}` con `{\"consentimiento\":true}` solicita al proveedor una prueba con la foto privada y la imagen HTTPS del producto; el backend nunca envía su token al navegador.
- El proveedor actual es [TryOn Labs](https://docs.tryonlabs.ai/) y ofrece créditos de prueba limitados. Regístrate y verifica el correo, inicia sesión para obtener un token de acceso y define `TRYONLABS_TOKEN` en el entorno del backend (o en `.env`, sin subirlo a Git). Este token puede caducar y requerir renovación.
- Para obtener el token, revisa los pasos de [autenticación](https://docs.tryonlabs.ai/authentication/). Si no se configura el token, guardar perfiles y fotografías sigue funcionando, pero generar la prueba devuelve HTTP 503.
- La integración funciona con las categorías `Tops` y `Bottoms`; requiere una imagen de prenda HTTPS pública. Los resultados de las pruebas no se guardan en MonarcaDB y se descartan del navegador al cerrar la vista. El proveedor externo puede tener sus propias reglas de retención y tratamiento de imágenes: presenta esta información al usuario antes de realizar pruebas reales.
- La IA genera una representación visual, no una medición real de ajuste ni una recomendación automática de talla.

**Pruebas de integración pendientes:** el pipeline valida la compilación, pero no consume créditos reales de TryOn Labs; la primera prueba extremo a extremo necesita un token activo y una fotografía/prenda de prueba autorizadas.

### Reducir el consumo de créditos del probador

Para pruebas, el backend utiliza por defecto `resolution=1k` y `generation_mode=fast`, en lugar de `2k` y `quality`. Se pueden cambiar sin editar código mediante `TRYON_RESOLUTION` y `TRYON_GENERATION_MODE` en el backend. **No existe una tarifa oficial confirmada para esta combinación:** revisa los créditos requeridos antes de volver a generar, porque una llamada puede consumir más de los 40 créditos restantes. No se consultan ni se gastan créditos al compilar o desplegar.

La documentación pública de TryOn Labs indica que una generación exitosa descuenta créditos, pero no proporciona una tabla detallada de precios por resolución y modo: https://docs.tryonlabs.ai/api/vton-generate-create/

### Diagnóstico HTTP 400 del probador (sin gastar créditos)

La documentación oficial confirma que `1k`, `2k`, `4k` y `fast`, `balanced`, `quality` son valores permitidos. Por tanto, HTTP 400 también puede deberse a imágenes u otros campos. Ahora el backend extrae exclusivamente mensajes de validación de campos conocidos de la respuesta JSON de TryOn Labs y los comunica a la interfaz sin incluir imágenes ni credenciales. El MIME de las imágenes se respeta además al construir el nombre de cada archivo del `multipart/form-data`.

**No se valida la API real en CI ni se gastan créditos.** Si el `.env` o IntelliJ tienen `TRYON_GENERATION_MODE=balanced`, esa variable prevalece y se debe cambiar manualmente a `fast`; no publicar tokens ni `.env`.
