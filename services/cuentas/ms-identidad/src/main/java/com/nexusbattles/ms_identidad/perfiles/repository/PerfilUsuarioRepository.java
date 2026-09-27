package com.nexusbattles.ms_identidad.perfiles.repository;

import com.nexusbattles.ms_identidad.perfiles.dto.PerfilPublicoResponse;
import com.nexusbattles.ms_identidad.perfiles.model.PerfilUsuario;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerfilUsuarioRepository extends JpaRepository<PerfilUsuario, Long> {

    /**
     * Caracter de escape del {@code LIKE} de {@link #buscarPublicosPorPrefijo}.
     *
     * <p>No es la barra invertida a proposito: la barra ya escapa en las
     * cadenas de Java y, segun el motor, en los literales de SQL, y una linea
     * que depende de varias capas de escapes es justo la que alguien
     * «arregla» sin saber que la rompe. {@code '!'} no significa nada ni en
     * Java, ni en HQL, ni en SQL. Quien construye el patron
     * (BusquedaDeJugadores) usa esta misma constante: si cambia aqui, cambia
     * en los dos sitios.
     */
    char ESCAPE_LIKE = '!';

    /**
     * B6 — cuentas ACTIVO cuyo apodo EMPIEZA por un texto, sin distinguir
     * mayusculas, con solo sus datos publicos (ms-identidad-perfiles.yaml,
     * {@code GET /api/v1/perfiles/publicos}).
     *
     * <ul>
     *   <li><b>{@code patron}</b> llega ya como prefijo ({@code texto%}) y con
     *       {@code %}, {@code _} y {@link #ESCAPE_LIKE} escapados: el
     *       {@code %} final es el unico comodin.</li>
     *   <li><b>{@code LOWER} a los dos lados</b>, y no {@code toLowerCase} en
     *       Java: asi las dos minusculas las calcula la misma base con las
     *       mismas reglas, y la expresion coincide con el indice
     *       {@code ix_usuarios_apodo_prefijo} (V4), que PostgreSQL usa para un
     *       {@code LIKE 'prefijo%'} en vez de recorrer la tabla.</li>
     *   <li><b>{@code LEFT JOIN}</b>: una cuenta sin perfil (las
     *       administrativas) tambien es alguien a quien escribir; sale con el
     *       avatar vacio.</li>
     *   <li><b>{@code publicId IS NOT NULL}</b>: el contrato promete {@code uid}
     *       en cada resultado y sin el no hay a quien mandar el mensaje. Hoy
     *       ninguna fila lo tiene nulo (lo rellena RellenoDeIdentificadorPublico
     *       al arrancar), pero la columna lo admite.</li>
     *   <li><b>Orden por apodo en minusculas</b> (y el apodo tal cual para
     *       desempatar): «ana», «Anabel», «anita» salen como las leeria una
     *       persona, sea cual sea la intercalacion de la base.</li>
     * </ul>
     *
     * El tope de resultados lo pone {@code pagina} (una sola, sin orden
     * propio: el orden es el de la consulta). Devuelve un {@code List}, no un
     * {@code Page}, para que Spring Data no lance ademas un {@code count(*)}
     * que nadie va a leer.
     */
    @Query("SELECT new com.nexusbattles.ms_identidad.perfiles.dto.PerfilPublicoResponse(u.publicId, u.apodo, p.avatar)"
            + " FROM Usuario u LEFT JOIN PerfilUsuario p ON p.usuario = u"
            + " WHERE u.estado = :estado AND u.publicId IS NOT NULL"
            + " AND LOWER(u.apodo) LIKE LOWER(:patron) ESCAPE '" + ESCAPE_LIKE + "'"
            + " ORDER BY LOWER(u.apodo), u.apodo")
    List<PerfilPublicoResponse> buscarPublicosPorPrefijo(@Param("patron") String patron,
                                                        @Param("estado") String estado,
                                                        Pageable pagina);

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