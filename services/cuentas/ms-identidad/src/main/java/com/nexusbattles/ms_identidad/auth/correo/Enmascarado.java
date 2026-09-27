package com.nexusbattles.ms_identidad.auth.correo;

/**
 * El correo de una persona tal como puede aparecer en la bitacora:
 * {@code v***a@dominio}. Suficiente para reconocer a quien se refiere un
 * aviso, insuficiente para usarlo (regla 6: la bitacora la lee mucha gente).
 * Misma forma que usa el servicio de correo en {@code GET /correos/envios}.
 */
public final class Enmascarado {

    private Enmascarado() {
    }

    public static String correo(String email) {
        if (email == null || email.isBlank()) {
            return "(sin correo)";
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        String local = email.substring(0, arroba);
        String dominio = email.substring(arroba + 1);
        String visible = local.length() == 1
                ? local + "***"
                : local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return visible + "@" + dominio;
    }
}
