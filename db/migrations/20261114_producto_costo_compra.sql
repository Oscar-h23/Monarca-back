-- Costo de compra interno para gestión administrativa; no se expone en el catálogo.
ALTER TABLE monarca.productos
  ADD COLUMN IF NOT EXISTS costo_compra NUMERIC(10,2);

-- Ejecutar una sola vez; si ya se aplicó mediante Supabase, omitir el CHECK duplicado.
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname='productos_costo_compra_no_negativo'
      AND conrelid='monarca.productos'::regclass
  ) THEN
    ALTER TABLE monarca.productos
      ADD CONSTRAINT productos_costo_compra_no_negativo
      CHECK (costo_compra IS NULL OR costo_compra >= 0);
  END IF;
END $$;
