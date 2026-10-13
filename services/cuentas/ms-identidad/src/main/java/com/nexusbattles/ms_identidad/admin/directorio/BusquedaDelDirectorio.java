package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * La busqueda del directorio como especificacion — RFINAL-06.
 *
 * <p>Reproduce {@code UsuarioRepository#buscarParaDirectorio}: el texto en el
 * apodo o en el correo, sin distinguir mayusculas, y sin escapar (como alli).
 * Existe para poder combinarla con {@link CuentasDePrueba#excluidas()}; que las
 * dos den lo mismo lo comprueba {@code DirectorioDeCuentasTest} contra una base.
 *
 * <p>HU-USR-008 (ms-identidad-admin.yaml 1.3.0): {@link #buscandoTambienPorNombre}
 * suma los nombres y apellidos del perfil. {@link #buscando} se queda como era,
 * porque es la que se compara con la consulta de siempre.
 *
 * <p>HU-USR-009 (#562, ms-identidad-admin.yaml 1.5.0):
 * {@link #buscandoTambienPorNombreEIdentificador} suma el identificador de la
 * cuenta, el mismo que acepta la ficha (HU-USR-010): su {@code uid} o su clave
 * {@code id}. Es la que usa el directorio con texto.
 */
public final class BusquedaDelDirectorio {

    /** Un {@code uid} escrito entero: 8-4-4-4-12 cifras hexadecimales, sin llaves ni prefijos. */
    private static final Pattern UID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /** La clave interna: solo cifras y como mucho 18, asi que siempre cabe en un {@code long}. */
    private static final Pattern CLAVE = Pattern.compile("[0-9]{1,18}");

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
     * HU-USR-009 — lo de {@link #buscandoTambienPorNombre} y, si el texto es un
     * identificador, ademas la cuenta que lo tiene. Es un O: la busqueda solo
     * gana resultados, nunca pierde los de antes.
     */
    public static Specification<Usuario> buscandoTambienPorNombreEIdentificador(String filtro) {
        Specification<Usuario> porTexto = buscandoTambienPorNombre(filtro);
        return porIdentificador(filtro).map(porTexto::or).orElse(porTexto);
    }

    /**
     * La cuenta con ese identificador exacto, si el texto lo es:
     * <ul>
     *   <li>un {@code uid} completo (sin distinguir mayusculas) &rarr; la cuenta
     *       con ese {@code public_id}; nunca el apodo del token ({@code sub}),
     *       que es otra cosa;</li>
     *   <li>solo cifras (hasta 18) &rarr; la cuenta con esa clave {@code id}.</li>
     * </ul>
     * Cualquier otro texto no es un identificador: vacio, sin intentar
     * convertirlo ni lanzar nada (un {@code uid} a medias o con letras de mas
     * se sigue buscando como texto, como siempre).
     */
    static Optional<Specification<Usuario>> porIdentificador(String filtro) {
        if (filtro == null || filtro.isEmpty()) {
            return Optional.empty();
        }
        if (UID.matcher(filtro).matches()) {
            UUID uid = UUID.fromString(filtro);
            return Optional.of((raiz, consulta, cb) -> cb.equal(raiz.get("publicId"), uid));
        }
        if (CLAVE.matcher(filtro).matches()) {
            long id = Long.parseLong(filtro);
            return Optional.of((raiz, consulta, cb) -> cb.equal(raiz.get("id"), id));
        }
        return Optional.empty();
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
