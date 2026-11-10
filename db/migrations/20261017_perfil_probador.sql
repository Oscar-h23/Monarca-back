ALTER TABLE monarca.usuarios
 ADD COLUMN IF NOT EXISTS direccion varchar(200),
 ADD COLUMN IF NOT EXISTS distrito varchar(100),
 ADD COLUMN IF NOT EXISTS provincia varchar(100),
 ADD COLUMN IF NOT EXISTS departamento varchar(100),
 ADD COLUMN IF NOT EXISTS referencia varchar(200);
CREATE TABLE IF NOT EXISTS monarca.fotos_probador (
 id_usuario bigint PRIMARY KEY REFERENCES monarca.usuarios(id_usuario) ON DELETE CASCADE,
 mime_tipo varchar(25) NOT NULL CHECK(mime_tipo IN ('image/jpeg','image/png','image/webp')),
 datos bytea NOT NULL CHECK(octet_length(datos) BETWEEN 1 AND 4000000),
 fecha_actualizacion timestamptz NOT NULL DEFAULT now()
);
