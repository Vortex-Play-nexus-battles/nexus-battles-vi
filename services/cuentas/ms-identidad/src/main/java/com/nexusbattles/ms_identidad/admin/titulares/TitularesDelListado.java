package com.nexusbattles.ms_identidad.admin.titulares;

import com.nexusbattles.ms_identidad.admin.directorio.ConsultaInvalidaException;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Los {@code uid} de un listado de otro servicio — SA-4 (ms-identidad-admin.yaml 1.9.0).
 *
 * <p>Las sanciones y las apelaciones de moderacion-sanciones nombran a la
 * cuenta por su {@code uid}; la consola de sanciones tiene que enseñar quien
 * es (su apodo), no un identificador. En vez de una consulta por fila, una
 * sola por pagina: {@code GET /admin/titulares?uids=a,b,c}
 * ({@link AdminTitularesController}).
 *
 * <p>Lo que no se puede aplicar se rechaza con un motivo para la persona
 * ({@link ConsultaInvalidaException}, 400): un {@code uid} mal escrito no es
 * «ninguna cuenta», es un error de quien pregunta.
 */
public final class TitularesDelListado {

    /** Las mismas que caben en una pagina del directorio. */
    public static final int MAXIMO = 100;

    private TitularesDelListado() {
    }

    /**
     * @param uids los {@code uid} separados por comas; null o en blanco = sin filtro
     * @return los {@code uid}, sin repetir y en el orden en que llegaron; vacio = sin filtro
     */
    public static Set<UUID> de(String uids) {
        if (uids == null || uids.isBlank()) {
            return Collections.emptySet();
        }
        Set<UUID> titulares = new LinkedHashSet<>();
        for (String parte : uids.split(",")) {
            String uid = parte.trim();
            if (uid.isEmpty()) {
                continue;
            }
            try {
                titulares.add(UUID.fromString(uid));
            } catch (IllegalArgumentException noEsUnUid) {
                throw new ConsultaInvalidaException(
                        "«" + recortado(uid) + "» no es el uid de una cuenta: escríbelo completo "
                                + "(por ejemplo, 11111111-2222-3333-4444-555555555555).");
            }
            if (titulares.size() > MAXIMO) {
                throw new ConsultaInvalidaException(
                        "Se pueden pedir como mucho " + MAXIMO + " cuentas a la vez.");
            }
        }
        return Collections.unmodifiableSet(titulares);
    }

    /** Solo las cuentas cuyo {@code uid} esta en la lista. */
    public static Specification<Usuario> solo(Set<UUID> uids) {
        return (raiz, consulta, cb) -> raiz.get("publicId").in(uids);
    }

    private static String recortado(String texto) {
        return texto.length() > 40 ? texto.substring(0, 40) + "…" : texto;
    }
}
