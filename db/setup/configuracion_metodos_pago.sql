-- Ya aplicado a Supabase MonarcaDB; script idempotente para documentación/despliegue.
ALTER TABLE monarca.metodos_pago
 ADD COLUMN IF NOT EXISTS telefono_yape varchar(20),
 ADD COLUMN IF NOT EXISTS qr_url text,
 ADD COLUMN IF NOT EXISTS titular varchar(120),
 ADD COLUMN IF NOT EXISTS banco varchar(120),
 ADD COLUMN IF NOT EXISTS numero_cuenta varchar(40),
 ADD COLUMN IF NOT EXISTS cci varchar(30);
-- El QR es público por diseño. Evidencias de pago permanecen en bucket privado.
