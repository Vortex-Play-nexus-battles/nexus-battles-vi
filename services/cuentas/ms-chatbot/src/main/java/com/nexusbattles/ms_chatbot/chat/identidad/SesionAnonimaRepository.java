package com.nexusbattles.ms_chatbot.chat.identidad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SesionAnonimaRepository extends JpaRepository<SesionAnonima, UUID> {

    Optional<SesionAnonima> findByHuella(String huella);

    // Consultas nativas con {h-schema}: Hibernate pone delante el esquema del
    // servicio (hibernate.default_schema=chatbot), igual que en las de JPA.
    //
    // La sesion que declaro un navegador y vencio se aparta para abrir otra:
    // su conversacion queda como la de cualquier sesion vencida (inalcanzable,
    // contada en las analiticas).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM {h-schema}sesiones_anonimas WHERE huella = :huella AND expira_en <= :ahora",
        nativeQuery = true)
    int borrarVencida(@Param("huella") String huella, @Param("ahora") Instant ahora);

    // Registrar la sesion que declara un navegador sin carrera: dos primeros
    // mensajes a la vez con el mismo identificador dejan una sola fila (la
    // huella es unica) y ninguno de los dos falla.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO {h-schema}sesiones_anonimas (id, huella, creada_en, ultima_actividad, expira_en) "
        + "VALUES (:id, :huella, :ahora, :ahora, :expiraEn) ON CONFLICT (huella) DO NOTHING", nativeQuery = true)
    int insertarSiNoExiste(@Param("id") UUID id, @Param("huella") String huella, @Param("ahora") Instant ahora,
                           @Param("expiraEn") Instant expiraEn);
}
