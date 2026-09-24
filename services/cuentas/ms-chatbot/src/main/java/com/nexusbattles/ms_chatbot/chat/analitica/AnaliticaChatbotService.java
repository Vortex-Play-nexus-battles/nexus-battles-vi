package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PuntoDeTendencia;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TemaFrecuente;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.repository.CalificacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// HU-CHA-012 (RF-CHA-012): calcula el tablero de analiticas del chatbot.
//
// Definiciones (acordadas en la planeacion de la HU):
//   conversaciones   conversaciones distintas con al menos una pregunta en el
//                    periodo (cada sesion reutiliza siempre su conversacion,
//                    asi que "creadas en el periodo" contaria de menos).
//   tasa resolucion  proporcion de respuestas del bot NO escaladas.
//   satisfaccion     proporcion de calificaciones "util".
//   tiempo           promedio de ms entre que llega la pregunta y sale la
//                    respuesta.
// Solo cuentan las respuestas generadas desde V4: las anteriores no guardaban
// si se escalaron ni cuanto tardaron.
@Service
public class AnaliticaChatbotService {

    public static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Bogota");

    static final int TOP_TEMAS = 10;

    // Un periodo mas largo haria cargar demasiados registros en memoria para
    // armar la tendencia diaria; un ano cubre el uso real de un tablero.
    static final int MAXIMO_DIAS = 366;

    private final MensajeRepository mensajeRepository;
    private final CalificacionRepository calificacionRepository;
    private final TemaConocimientoRepository temaConocimientoRepository;

    public AnaliticaChatbotService(MensajeRepository mensajeRepository, CalificacionRepository calificacionRepository,
                                   TemaConocimientoRepository temaConocimientoRepository) {
        this.mensajeRepository = mensajeRepository;
        this.calificacionRepository = calificacionRepository;
        this.temaConocimientoRepository = temaConocimientoRepository;
    }

    // 'desde' y 'hasta' son dias completos e inclusivos en 'zona'.
    @Transactional(readOnly = true)
    public AnaliticaChatbot calcular(LocalDate desde, LocalDate hasta, ZoneId zona) {
        validarPeriodo(desde, hasta);
        Instant inicio = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();

        List<RegistroDePregunta> preguntas = mensajeRepository.buscarPreguntasEntre(Remitente.USUARIO, inicio, fin);
        List<RegistroDeRespuesta> respuestas = mensajeRepository.buscarRespuestasMedidasEntre(Remitente.BOT, inicio, fin);
        List<Boolean> calificaciones = calificacionRepository.buscarUtilidadEntre(inicio, fin);

        long conversaciones = preguntas.stream().map(RegistroDePregunta::conversacionId).distinct().count();
        long escalamientos = respuestas.stream().filter(r -> Boolean.TRUE.equals(r.escalado())).count();
        long calificacionesUtiles = calificaciones.stream().filter(Boolean.TRUE::equals).count();

        return new AnaliticaChatbot(
            inicio,
            fin,
            zona.getId(),
            conversaciones,
            preguntas.size(),
            respuestas.size(),
            escalamientos,
            proporcion(respuestas.size() - escalamientos, respuestas.size()),
            tiempoPromedio(respuestas),
            calificaciones.size(),
            calificacionesUtiles,
            proporcion(calificacionesUtiles, calificaciones.size()),
            temasFrecuentes(inicio, fin),
            tendencia(desde, hasta, zona, preguntas, respuestas)
        );
    }

    private void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo necesita fecha 'desde' y 'hasta'.");
        }
        if (hasta.isBefore(desde)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha 'hasta' no puede ser anterior a 'desde'.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El periodo no puede superar " + MAXIMO_DIAS + " dias.");
        }
    }

    private List<TemaFrecuente> temasFrecuentes(Instant inicio, Instant fin) {
        List<ConteoDeTema> conteos = mensajeRepository.contarRespuestasPorTemaEntre(
            Remitente.BOT, inicio, fin, PageRequest.of(0, TOP_TEMAS));
        if (conteos.isEmpty()) {
            return List.of();
        }
        Set<String> claves = conteos.stream().map(ConteoDeTema::temaClave).collect(Collectors.toSet());
        // Una clave puede tener varias copias (una por version); todas
        // comparten el tema, asi que cualquiera sirve para mostrar el titulo.
        Map<String, String> titulos = temaConocimientoRepository.findByClaveIn(claves).stream()
            .collect(Collectors.toMap(TemaConocimiento::getClave, TemaConocimiento::getTitulo, (a, b) -> a));

        return conteos.stream()
            // Si el tema ya no existe en ninguna version, se muestra su clave.
            .map(c -> new TemaFrecuente(c.temaClave(), titulos.getOrDefault(c.temaClave(), c.temaClave()),
                c.respuestas()))
            .toList();
    }

    private List<PuntoDeTendencia> tendencia(LocalDate desde, LocalDate hasta, ZoneId zona,
                                             List<RegistroDePregunta> preguntas,
                                             List<RegistroDeRespuesta> respuestas) {
        Map<LocalDate, Long> preguntasPorDia = new HashMap<>();
        Map<LocalDate, Set<UUID>> conversacionesPorDia = new HashMap<>();
        for (RegistroDePregunta pregunta : preguntas) {
            LocalDate dia = LocalDate.ofInstant(pregunta.fechaEnvio(), zona);
            preguntasPorDia.merge(dia, 1L, Long::sum);
            conversacionesPorDia.computeIfAbsent(dia, d -> new HashSet<>()).add(pregunta.conversacionId());
        }

        Map<LocalDate, Long> respuestasPorDia = new HashMap<>();
        Map<LocalDate, Long> escalamientosPorDia = new HashMap<>();
        for (RegistroDeRespuesta respuesta : respuestas) {
            LocalDate dia = LocalDate.ofInstant(respuesta.fechaEnvio(), zona);
            respuestasPorDia.merge(dia, 1L, Long::sum);
            if (Boolean.TRUE.equals(respuesta.escalado())) {
                escalamientosPorDia.merge(dia, 1L, Long::sum);
            }
        }

        // Un punto por cada dia del periodo, tambien los dias sin actividad:
        // sin ellos, una grafica de tendencia saltaria dias sin avisar.
        List<PuntoDeTendencia> puntos = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            puntos.add(new PuntoDeTendencia(
                dia,
                conversacionesPorDia.getOrDefault(dia, Set.of()).size(),
                preguntasPorDia.getOrDefault(dia, 0L),
                respuestasPorDia.getOrDefault(dia, 0L),
                escalamientosPorDia.getOrDefault(dia, 0L)));
        }
        return puntos;
    }

    private static Double tiempoPromedio(List<RegistroDeRespuesta> respuestas) {
        return respuestas.stream()
            .map(RegistroDeRespuesta::tiempoRespuestaMs)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .average()
            .stream().boxed().findFirst().orElse(null);
    }

    private static Double proporcion(long parte, long total) {
        return total == 0 ? null : (double) parte / total;
    }
}
