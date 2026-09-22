package com.nexusbattles.ms_identidad.auth.validation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Política de contraseñas — RF-AUT-002: más de 8 caracteres y las cuatro
 * clases (mayúscula, minúscula, número y símbolo).
 *
 * <p>Desde HU-AUT-006 el rechazo dice <b>qué regla</b> falla (CA-03), no solo
 * que falla: «te falta un número» se puede corregir; «no cumple la política»
 * obliga a adivinar. La misma pieza la usan el registro (HU-AUT-001), el canje
 * de recuperación (HU-COR-003) y el cambio de contraseña.
 */
@Component
public class PasswordPolicyValidator {

    static final int LONGITUD_MINIMA_EXCLUSIVA = 8;

    public void validar(String password) {
        List<String> faltan = reglasQueFaltan(password);
        if (!faltan.isEmpty()) {
            throw new IllegalArgumentException(
                    "La contraseña no cumple la política: " + String.join("; ", faltan) + ".");
        }
    }

    /**
     * Reglas incumplidas, en el orden en que se leen. Vacía si la contraseña
     * cumple. Pública para que quien muestre el error pueda listarlas.
     */
    public List<String> reglasQueFaltan(String password) {
        List<String> faltan = new ArrayList<>();
        if (password == null || password.length() <= LONGITUD_MINIMA_EXCLUSIVA) {
            faltan.add("debe tener más de " + LONGITUD_MINIMA_EXCLUSIVA + " caracteres");
        }
        if (password == null) {
            return faltan;
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            faltan.add("debe incluir al menos una mayúscula");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            faltan.add("debe incluir al menos una minúscula");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            faltan.add("debe incluir al menos un número");
        }
        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            faltan.add("debe incluir al menos un símbolo");
        }
        return faltan;
    }
}
