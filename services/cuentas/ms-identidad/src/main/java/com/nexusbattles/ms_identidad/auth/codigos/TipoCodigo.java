package com.nexusbattles.ms_identidad.auth.codigos;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Para que sirve un codigo enviado al correo.
 *
 * <p>Cada tipo pertenece a una <b>familia</b>: los codigos que se anulan entre
 * si al emitir uno nuevo y que se buscan juntos al canjear. La verificacion
 * del correo va sola. La activacion de una cuenta administrativa y el
 * restablecimiento de contrasena van juntos porque hacen lo mismo —la
 * persona demuestra que el buzon es suyo y fija su propia contrasena en
 * {@code POST /auth/restablecer/confirmar}—: si un administrador restablece
 * la contrasena de una cuenta que aun no se activo, el codigo nuevo sustituye
 * al de activacion en vez de dejar dos secretos vivos para la misma puerta.
 */
public enum TipoCodigo {

    /** Autorregistro: prueba que el correo es de quien se registra (B1, 7.4.12). */
    VERIFICACION,
    /** Cuenta creada por un Super Administrador: la persona fija su contrasena (HU-USR-002). */
    ACTIVACION,
    /** Contrasena olvidada o restablecida desde el panel (HU-COR-003, 7.1.1). */
    RESTABLECIMIENTO;

    public Set<TipoCodigo> familia() {
        return this == VERIFICACION ? EnumSet.of(VERIFICACION) : EnumSet.of(ACTIVACION, RESTABLECIMIENTO);
    }

    /** La familia con los nombres que guarda la columna {@code tipo}. */
    public List<String> familiaComoTexto() {
        return familia().stream().map(Enum::name).toList();
    }
}
