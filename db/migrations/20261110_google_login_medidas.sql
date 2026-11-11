-- Ejecutar antes de desplegar el backend: ddl-auto=validate requiere estas columnas.
-- No se alteran contraseñas ni datos de clientes ya existentes.
ALTER TABLE monarca.usuarios
  ADD COLUMN IF NOT EXISTS google_sub varchar(255),
  ADD COLUMN IF NOT EXISTS busto_cm numeric(5,1),
  ADD COLUMN IF NOT EXISTS cintura_cm numeric(5,1),
  ADD COLUMN IF NOT EXISTS cadera_cm numeric(5,1),
  ADD COLUMN IF NOT EXISTS altura_cm numeric(5,1);

CREATE UNIQUE INDEX IF NOT EXISTS ux_monarca_usuarios_google_sub
  ON monarca.usuarios (google_sub) WHERE google_sub IS NOT NULL;

ALTER TABLE monarca.variantes_producto
  ADD COLUMN IF NOT EXISTS pecho_cm numeric(5,1),
  ADD COLUMN IF NOT EXISTS cintura_cm numeric(5,1),
  ADD COLUMN IF NOT EXISTS cadera_cm numeric(5,1),
  ADD COLUMN IF NOT EXISTS largo_cm numeric(5,1);

-- Sin valores iniciales inventados: NULL significa medida pendiente.
