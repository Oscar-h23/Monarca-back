-- Preparación idempotente para entorno de pruebas Monarca / Nayeli's moda.
-- Evidencias en bucket PRIVADO: nunca dar acceso público ni enviar service-role al navegador.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('monarca-evidencias-pago', 'monarca-evidencias-pago',
        false, 5000000, ARRAY['image/jpeg','image/png'])
ON CONFLICT (id) DO NOTHING;

-- Opciones manuales: confirmar los datos de cobro directamente con la tienda;
-- estas filas no configuran una cuenta bancaria ni un número Yape.
INSERT INTO monarca.metodos_pago (codigo, nombre, descripcion, requiere_comprobante, activo)
VALUES
 ('YAPE', 'Yape', 'Pago manual sujeto a revisión', true, true),
 ('TRANSFERENCIA', 'Transferencia bancaria', 'Pago manual sujeto a revisión', true, true)
ON CONFLICT (codigo) DO NOTHING;

-- La entidad monarca.pagos ya contiene estado, comprobante_url (ruta privada),
-- observacion, fecha_pago y fecha_confirmacion: sin ALTER TABLE.
