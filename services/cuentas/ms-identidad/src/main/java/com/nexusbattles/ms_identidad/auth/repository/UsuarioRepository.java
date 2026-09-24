package com.nexusbattles.ms_identidad.auth.repository;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByApodo(String apodo);
    long countByRol(RolEntity rol);

    /**
     * R17 — el alta rechaza un correo o un apodo que ya existen escritos con
     * otras mayusculas: «Profe@upb.edu.co» y «profe@upb.edu.co» son la misma
     * persona, y «Valkiria» y «valkiria» serian dos jugadores indistinguibles
     * en una sala. Es un recuento, no una busqueda: si una base heredada
     * tuviera ya dos filas que solo difieren en mayusculas, no revienta.
     */
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByApodoIgnoreCase(String apodo);

    /**
     * El usuario de un correo tal como lo teclea quien inicia sesion.
     *
     * <p>Desde R17 el registro guarda el correo en minusculas; las cuentas
     * anteriores lo tienen como se escribio. Se busca primero exacto (sin
     * espacios alrededor) —asi una cuenta antigua sigue entrando igual que
     * antes— y despues en minusculas, que es como estan las nuevas. Sin
     * consultas «ignore case», que en una base heredada con duplicados por
     * mayusculas devolverian dos filas.
     */
    default Optional<Usuario> buscarPorCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            return Optional.empty();
        }
        String limpio = correo.trim();
        Optional<Usuario> exacto = findByEmail(limpio);
        if (exacto.isPresent()) {
            return exacto;
        }
        String minusculas = limpio.toLowerCase(Locale.ROOT);
        return minusculas.equals(limpio) ? Optional.empty() : findByEmail(minusculas);
    }

    /**
     * Usuarios que todavia no tienen identificador publico, porque se crearon
     * antes de que el campo existiera. Los rellena
     * {@code RellenoDeIdentificadorPublico} al arrancar. Cuando el servicio
     * tenga Flyway (R8) y la columna sea NOT NULL, esta consulta deja de
     * devolver nada y el relleno se puede retirar.
     */
    List<Usuario> findByPublicIdIsNull();

    /**
     * Directorio administrativo: una pagina de usuarios, opcionalmente
     * filtrada por apodo o correo.
     *
     * Paginada a proposito. La consola no puede pedir «todos los usuarios» y
     * quedarse esperando: esa consulta crece sin limite con el tiempo y la
     * primera pantalla que ve quien opera el sistema seria la mas lenta.
     *
     * El filtro compara en minusculas por los dos campos por los que un
     * administrador busca de verdad -- «me han reportado a fulanito» o «este
     * correo dice que no puede entrar» -- y nunca por contrasena ni por
     * ningun campo que la tabla no muestre.
     */
    /**
     * Sin filtro se pasa cadena vacia, NUNCA null.
     *
     * La primera version decia {@code WHERE :filtro IS NULL OR ...}. Compila,
     * arranca, y en H2 hasta funciona; contra PostgreSQL el controlador manda
     * un null sin tipo y el servidor no puede deducir de que tipo es el
     * parametro: la consulta sin filtro -- o sea, la primera pantalla que ve
     * quien abre el directorio -- devolvia 500. Con cadena vacia,
     * {@code LIKE '%%'} acepta cualquier valor y hay un solo camino de codigo
     * en vez de dos.
     */
    @Query("SELECT u FROM Usuario u"
            + " WHERE LOWER(u.apodo) LIKE LOWER(CONCAT('%', :filtro, '%'))"
            + " OR LOWER(u.email) LIKE LOWER(CONCAT('%', :filtro, '%'))")
    Page<Usuario> buscarParaDirectorio(@Param("filtro") String filtro, Pageable pagina);
}
