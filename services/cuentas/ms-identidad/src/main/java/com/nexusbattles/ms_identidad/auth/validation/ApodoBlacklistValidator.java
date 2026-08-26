// src/main/java/com/nexusbattles/ms_identidad/auth/validation/ApodoBlacklistValidator.java
package com.nexusbattles.ms_identidad.auth.validation;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApodoBlacklistValidator {

    // Misma lista temporal que ya usa RegistroService (HU-AUT-001).
    // TODO [INTEGRACIÓN FUTURA]: reemplazar por el servicio de Lista Negra
    // del equipo de Felipe (HU-ADM-002 / RF-ADM-002).
    private static final List<String> APODOS_PROHIBIDOS_TEMPORAL = List.of(
            "admin", "root", "system", "moderador", "sex", "hack"
    );

    public void validar(String apodo) {
        if (apodo == null) return;
        String apodoNormalizado = apodo.toLowerCase().trim();
        for (String prohibido : APODOS_PROHIBIDOS_TEMPORAL) {
            if (apodoNormalizado.contains(prohibido)) {
                throw new IllegalArgumentException(
                        "El apodo contiene términos prohibidos por la política de la comunidad.");
            }
        }
    }
}