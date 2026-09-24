package com.nexusbattles.ms_identidad.perfiles.repository;

import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerfilUsuarioRepository extends JpaRepository<PerfilUsuario, Long> {

    /**
     * Perfil del USUARIO con ese id interno.
     *
     * <p>Hasta R16 filtraba por {@code p.id} —el id del perfil— aunque el
     * parámetro se llama {@code usuarioId}. Funcionaba solo mientras cada
     * usuario tuviera exactamente un perfil creado en el mismo orden: una
     * cuenta dada de alta sin perfil (las de administrador) desalinea las dos
     * secuencias y, desde ahí, cada jugador nuevo recibiría el perfil de otro,
     * que {@code verificarDueno} convertía en un 403 inexplicable.
     */
    @Query("SELECT p FROM PerfilUsuario p JOIN FETCH p.usuario u WHERE u.id = :usuarioId")
    Optional<PerfilUsuario> findByIdConUsuario(Long usuarioId);

    /**
     * Perfil del usuario con ese identificador público: el mismo UUID que
     * viaja en el claim {@code uid} del token (ADR-002) y que el frontend
     * tiene en su sesión. Es el único identificador que el navegador conoce.
     */
    @Query("SELECT p FROM PerfilUsuario p JOIN FETCH p.usuario u WHERE u.publicId = :identificadorPublico")
    Optional<PerfilUsuario> findByIdentificadorPublicoConUsuario(UUID identificadorPublico);
}