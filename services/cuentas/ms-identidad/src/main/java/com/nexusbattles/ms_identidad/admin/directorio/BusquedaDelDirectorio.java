package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * La busqueda del directorio como especificacion — RFINAL-06.
 *
 * <p>Reproduce {@code UsuarioRepository#buscarParaDirectorio}: el texto en el
 * apodo o en el correo, sin distinguir mayusculas, y sin escapar (como alli).
 * Existe para poder combinarla con {@link CuentasDePrueba#excluidas()}; que las
 * dos den lo mismo lo comprueba {@code DirectorioDeCuentasTest} contra una base.
 *
 * <p>HU-USR-008 (ms-identidad-admin.yaml 1.3.0): {@link #buscandoTambienPorNombre}
 * suma los nombres y apellidos del perfil. Es la que usa el directorio con
 * texto; {@link #buscando} se queda como era, porque es la que se compara con
 * la consulta de siempre.
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

    /**
     * HU-USR-008 — el texto en el apodo, en el correo o en el nombre del perfil.
     *
     * <p>Lo que {@link #buscando} encontraba lo sigue encontrando (es un O): la
     * busqueda solo gana resultados. Sin texto, todas las cuentas.
     */
    public static Specification<Usuario> buscandoTambienPorNombre(String filtro) {
        if (filtro == null || filtro.isEmpty()) {
            return buscando(filtro);
        }
        return buscando(filtro).or(porNombreDelPerfil(filtro));
    }

    /**
     * Cuentas cuyo perfil tiene ese texto en «nombres apellidos», sin distinguir
     * mayusculas: «ana perez» encuentra a Ana (nombres) Perez (apellidos), y
     * «perez» o «ana» tambien.
     *
     * <p>Subconsulta {@code EXISTS} y no un JOIN: la cuenta no conoce su perfil
     * (la relacion la lleva {@code PerfilUsuario}, con {@code @MapsId}), una
     * cuenta sin perfil (las administrativas) no puede desaparecer del
     * directorio por no tenerlo, y un JOIN obligaria a cuidar el recuento de la
     * paginacion. Mismo patron sin escapar que {@link #buscando}.
     */
    static Specification<Usuario> porNombreDelPerfil(String filtro) {
        return (raiz, consulta, cb) -> {
            String patron = "%" + filtro.toLowerCase(Locale.ROOT) + "%";
            Subquery<Long> perfil = consulta.subquery(Long.class);
            Root<PerfilUsuario> p = perfil.from(PerfilUsuario.class);
            Expression<String> nombreCompleto =
                    cb.concat(cb.concat(p.<String>get("nombres"), " "), p.<String>get("apellidos"));
            perfil.select(p.<Long>get("id")).where(
                    cb.equal(p.get("usuario"), raiz),
                    cb.like(cb.lower(nombreCompleto), patron));
            return cb.exists(perfil);
        };
    }
}
