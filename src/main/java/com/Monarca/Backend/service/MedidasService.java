package com.Monarca.Backend.service;

import java.math.BigDecimal;

/** Rangos plausibles en centímetros; una medida ausente se guarda como NULL. */
public final class MedidasService {
    private MedidasService() {}

    public static BigDecimal validar(BigDecimal cm, String nombre, int minimo, int maximo) {
        if (cm == null) return null;
        if (cm.scale() > 1 || cm.compareTo(BigDecimal.valueOf(minimo)) < 0
                || cm.compareTo(BigDecimal.valueOf(maximo)) > 0) {
            throw new IllegalArgumentException(nombre + ": indica centímetros entre "
                    + minimo + " y " + maximo + " (máximo un decimal).");
        }
        return cm;
    }

    public static void variante(com.Monarca.Backend.model.VarianteProducto v,
                                BigDecimal pecho, BigDecimal cintura,
                                BigDecimal cadera, BigDecimal largo) {
        v.setPechoCm(validar(pecho, "Pecho de la prenda", 20, 300));
        v.setCinturaCm(validar(cintura, "Cintura de la prenda", 20, 300));
        v.setCaderaCm(validar(cadera, "Cadera de la prenda", 20, 300));
        v.setLargoCm(validar(largo, "Largo de la prenda", 10, 300));
    }
}
