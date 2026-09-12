package com.nexusbattles.ms_identidad.auth.repository;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
