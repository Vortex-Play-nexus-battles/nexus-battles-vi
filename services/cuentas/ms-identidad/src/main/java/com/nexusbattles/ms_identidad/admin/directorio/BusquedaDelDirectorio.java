package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * La busqueda del directorio como especificacion — RFINAL-06.
 *
 * <p>Reproduce {@code UsuarioRepository#buscarParaDirectorio}: el texto en el
 * apodo o en el correo, sin distinguir mayusculas, y sin escapar (como alli).
 * Existe para poder combinarla con {@link CuentasDePrueba#excluidas()}; que las
 * dos den lo mismo lo comprueba {@code DirectorioDeCuentasTest} contra una base.
 */
public final class BusquedaDelDirectorio {

    private BusquedaDelDirectorio() {
    }

    /**
     * @param filtro texto ya recortado; cadena vacia (o nula) = todas las cuentas
     */
    public static Specification<Usuario> buscando(String filtro) {
        return (raiz, consulta, cb) -> {
            if (filtro == null || filtro.isEmpty()) {
                return cb.conjunction();
            }
            String patron = "%" + filtro.toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(raiz.get("apodo")), patron),
                    cb.like(cb.lower(raiz.get("email")), patron));
        };
    }
}
