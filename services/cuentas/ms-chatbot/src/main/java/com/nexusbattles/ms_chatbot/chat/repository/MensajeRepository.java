package com.nexusbattles.ms_chatbot.chat.repository;

import com.nexusbattles.ms_chatbot.chat.analitica.ConteoDeTema;
import com.nexusbattles.ms_chatbot.chat.analitica.RegistroDePregunta;
import com.nexusbattles.ms_chatbot.chat.analitica.RegistroDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MensajeRepository extends JpaRepository<Mensaje, UUID> {

    List<Mensaje> findByConversacionIdOrderByFechaEnvioAsc(UUID conversacionId);

    // Soporta el criterio "limpiar historial" de HU-CHA-001.
    void deleteByConversacionId(UUID conversacionId);

    // HU-CHA-011: busca el mensaje SOLO si pertenece a la conversacion de
    // quien llama. Un mensaje de otra sesion se comporta igual que uno
    // inexistente, asi nadie puede calificar (ni confirmar que existe) una
    // respuesta de una conversacion ajena adivinando su UUID.
    Optional<Mensaje> findByIdAndConversacionIdentificadorSesion(UUID id, String identificadorSesion);

    // HU-CHA-011: la pregunta del usuario que origino una respuesta del bot
    // es el mensaje de USUARIO mas reciente de la misma conversacion con
    // fecha anterior o igual a la de esa respuesta. Se usa "o igual" porque
    // ambos mensajes se guardan en la misma transaccion y, en teoria, podrian
    // compartir marca de tiempo; el filtro por remitente evita que la propia
    // respuesta del bot cuente como su pregunta.
    Optional<Mensaje> findFirstByConversacionIdAndRemitenteAndFechaEnvioLessThanEqualOrderByFechaEnvioDesc(
        UUID conversacionId, Remitente remitente, Instant fechaEnvio);

    // HU-CHA-012 (analiticas). Todas filtran por [desde, hasta) sobre
    // fecha_envio, que tiene indice desde V4. Devuelven proyecciones livianas,
    // no entidades completas, porque solo se cuentan y se agrupan.

    @Query("""
        select new com.nexusbattles.ms_chatbot.chat.analitica.RegistroDePregunta(m.fechaEnvio, m.conversacion.id)
        from Mensaje m
        where m.remitente = :remitente
          and m.fechaEnvio >= :desde and m.fechaEnvio < :hasta
        """)
    List<RegistroDePregunta> buscarPreguntasEntre(@Param("remitente") Remitente remitente,
                                                  @Param("desde") Instant desde,
                                                  @Param("hasta") Instant hasta);

    // Solo respuestas medidas (escalado no nulo): las anteriores a V4 no
    // tienen estos datos y distorsionarian la tasa de resolucion.
    @Query("""
        select new com.nexusbattles.ms_chatbot.chat.analitica.RegistroDeRespuesta(
            m.fechaEnvio, m.escalado, m.tiempoRespuestaMs)
        from Mensaje m
        where m.remitente = :remitente
          and m.escalado is not null
          and m.fechaEnvio >= :desde and m.fechaEnvio < :hasta
        """)
    List<RegistroDeRespuesta> buscarRespuestasMedidasEntre(@Param("remitente") Remitente remitente,
                                                           @Param("desde") Instant desde,
                                                           @Param("hasta") Instant hasta);

    @Query("""
        select new com.nexusbattles.ms_chatbot.chat.analitica.ConteoDeTema(m.temaClave, count(m))
        from Mensaje m
        where m.remitente = :remitente
          and m.temaClave is not null
          and m.fechaEnvio >= :desde and m.fechaEnvio < :hasta
        group by m.temaClave
        order by count(m) desc
        """)
    List<ConteoDeTema> contarRespuestasPorTemaEntre(@Param("remitente") Remitente remitente,
                                                    @Param("desde") Instant desde,
                                                    @Param("hasta") Instant hasta,
                                                    Pageable limite);
}
