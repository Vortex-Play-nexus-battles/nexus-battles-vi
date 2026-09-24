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
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByApodo(String apodo);
    long countByRol(RolEntity rol);

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
    @Query("SELECT u FROM Usuario u WHERE :filtro IS NULL"
            + " OR LOWER(u.apodo) LIKE LOWER(CONCAT('%', :filtro, '%'))"
            + " OR LOWER(u.email) LIKE LOWER(CONCAT('%', :filtro, '%'))")
    Page<Usuario> buscarParaDirectorio(@Param("filtro") String filtro, Pageable pagina);
}
