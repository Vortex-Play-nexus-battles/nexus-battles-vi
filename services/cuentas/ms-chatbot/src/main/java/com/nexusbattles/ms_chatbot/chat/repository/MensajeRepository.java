package com.nexusbattles.ms_chatbot.chat.repository;

import com.nexusbattles.ms_chatbot.chat.analitica.ConteoDeTema;
import com.nexusbattles.ms_chatbot.chat.analitica.RegistroDePregunta;
import com.nexusbattles.ms_chatbot.chat.analitica.RegistroDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.analitica.RegistroDeTexto;
import com.nexusbattles.ms_chatbot.chat.model.Mensaje;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MensajeRepository extends JpaRepository<Mensaje, UUID> {


    // ms-chatbot.yaml 1.3.5: historial por paginas, del mas reciente hacia
    // atras. Los empates de hora se deciden por id, para que ningun mensaje se
    // repita ni se pierda entre una pagina y la siguiente.
    List<Mensaje> findByConversacionIdOrderByFechaEnvioDescIdDesc(UUID conversacionId, Pageable limite);

    // 1.3.5: el cursor 'antesDe' solo vale si es de la propia conversacion.
    Optional<Mensaje> findByIdAndConversacionId(UUID id, UUID conversacionId);

    @Query("""
        select m from Mensaje m
        where m.conversacion.id = :conversacionId
          and (m.fechaEnvio < :fecha or (m.fechaEnvio = :fecha and m.id < :id))
        order by m.fechaEnvio desc, m.id desc
        """)
    List<Mensaje> buscarAnteriores(@Param("conversacionId") UUID conversacionId,
                                   @Param("fecha") Instant fecha,
                                   @Param("id") UUID id,
                                   Pageable limite);

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

    // 1.15.0 (7.4.5, D-CHA-13): cuantas preguntas de esta persona resolvio ya
    // el asistente, en su propia conversacion. Es su experiencia con el
    // asistente: con ella se elige el nivel de detalle automatico.
    long countByConversacionIdentificadorSesionAndRemitenteAndResultadoIn(String identificadorSesion,
                                                                          Remitente remitente,
                                                                          Collection<ResultadoDeRespuesta> resultados);

    // 1.15.0 (7.4.3 «sugerencias proactivas», D-CHA-14): los temas de la base
    // que le respondieron a esta persona, del mas reciente al mas antiguo (con
    // repetidos: se quitan al usarlos). Solo su conversacion: la clave sale de
    // su identidad verificada.
    @Query("""
        select m.temaClave from Mensaje m
        where m.conversacion.identificadorSesion = :clave
          and m.remitente = :remitente
          and m.resultado = :resultado
          and m.temaClave is not null
        order by m.fechaEnvio desc, m.id desc
        """)
    List<String> temasQueLeRespondieron(@Param("clave") String identificadorSesion,
                                        @Param("remitente") Remitente remitente,
                                        @Param("resultado") ResultadoDeRespuesta resultado,
                                        Pageable limite);

    // 1.5.0 (seguimiento del dialogo): la ultima respuesta del bot en esta
    // conversacion; «mas detalle» o «en ingles» se refieren a ella. Solo la
    // conversacion de quien pregunta: la clave sale de su identidad verificada.
    Optional<Mensaje> findFirstByConversacionIdentificadorSesionAndRemitenteOrderByFechaEnvioDesc(
        String identificadorSesion, Remitente remitente);

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
    // tienen estos datos y distorsionarian el tiempo y los escalamientos.
    // 1.7.0: con su resultado (null antes de V13), del que sale la tasa de
    // resolucion.
    @Query("""
        select new com.nexusbattles.ms_chatbot.chat.analitica.RegistroDeRespuesta(
            m.fechaEnvio, m.escalado, m.tiempoRespuestaMs, m.resultado)
        from Mensaje m
        where m.remitente = :remitente
          and m.escalado is not null
          and m.fechaEnvio >= :desde and m.fechaEnvio < :hasta
        """)
    List<RegistroDeRespuesta> buscarRespuestasMedidasEntre(@Param("remitente") Remitente remitente,
                                                           @Param("desde") Instant desde,
                                                           @Param("hasta") Instant hasta);

    // 1.3.7: el texto de las preguntas mas recientes del periodo, solo para
    // contar palabras clave (PalabrasClaveFrecuentes).
    @Query("""
        select new com.nexusbattles.ms_chatbot.chat.analitica.RegistroDeTexto(m.conversacion.id, m.contenido)
        from Mensaje m
        where m.remitente = :remitente
          and m.fechaEnvio >= :desde and m.fechaEnvio < :hasta
        order by m.fechaEnvio desc
        """)
    List<RegistroDeTexto> buscarTextosDePreguntasEntre(@Param("remitente") Remitente remitente,
                                                       @Param("desde") Instant desde,
                                                       @Param("hasta") Instant hasta,
                                                       Pageable limite);

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
