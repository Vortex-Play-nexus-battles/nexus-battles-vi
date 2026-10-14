package com.nexusbattles.ms_chatbot.chat.analitica;

import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.PuntoDeTendencia;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.RespuestasPorResultado;
import com.nexusbattles.ms_chatbot.chat.analitica.AnaliticaChatbot.TemaFrecuente;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.repository.CalificacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.ConversacionRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import com.nexusbattles.ms_chatbot.chat.soporte.TicketSoporteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// HU-CHA-012 (RF-CHA-012): calcula el tablero de analiticas del chatbot.
//
// Definiciones (ms-chatbot.yaml 1.7.0, 7.4.7; la explicacion larga esta en el
// contrato, campo por campo):
//   conversaciones   las INICIADAS en el periodo (conversaciones.fecha_inicio).
//                    Borrar un historial no las cambia: la fila queda. Hasta
//                    1.6.0 eran las que tenian alguna pregunta en el periodo,
//                    y eso sigue como conversacionesConActividad.
//   tasa resolucion  resueltas / clasificadas: de las respuestas con
//                    resultado guardado (V13), las que resolvio un tema, una
//                    consulta en vivo con datos o una navegacion. Las de antes
//                    de V13 no cuentan. Hasta 1.6.0 era «no escaladas», y un
//                    «servicio no disponible» contaba como resuelto.
//   escalamientos    veces que el ASISTENTE ofrecio soporte humano (escalado
//                    o «no me sirvio»). Las solicitudes que de verdad se
//                    abrieron son tickets.total.
//   satisfaccion     proporcion de calificaciones "util".
//   tiempo           promedio de ms que tarda el servidor en generar la
//                    respuesta: desde que la pregunta, ya revisada y aprobada
//                    por la lista negra, empieza a procesarse hasta que la
//                    respuesta esta lista para guardarse (ChatService).
//   tickets          (1.3.7) los abiertos en el periodo, por estado y
//                    categoria (AnaliticaDeTickets).
//   palabras clave   (1.3.7) las mas usadas en las preguntas del periodo, de
//                    las ultimas MAXIMO_PREGUNTAS_PARA_PALABRAS
//                    (PalabrasClaveFrecuentes); desde 1.7.0 con su tendencia
//                    contra el periodo anterior de los mismos dias.
//   preguntas        (1.7.0) las mas repetidas, de esas mismas preguntas,
//   frecuentes       solo si las hicieron 3 conversaciones (PreguntasFrecuentes).
// Los tiempos y escalamientos cuentan desde V4: las anteriores no guardaban
// si se escalaron ni cuanto tardaron.
@Service
public class AnaliticaChatbotService {

    public static final ZoneId ZONA_POR_DEFECTO = PeriodoDelPanel.ZONA;

    static final int TOP_TEMAS = 10;
    static final int TOP_PALABRAS = 15;
    static final int TOP_PREGUNTAS = 10;

    // Las palabras clave y las preguntas frecuentes se cuentan en memoria: con
    // las ultimas 5000 preguntas del periodo basta para ver de que se habla,
    // sin cargar un ano entero.
    static final int MAXIMO_PREGUNTAS_PARA_PALABRAS = 5000;

    // Un periodo mas largo haria cargar demasiados registros en memoria para
    // armar la tendencia diaria; un ano cubre el uso real de un tablero.
    static final int MAXIMO_DIAS = PeriodoDelPanel.MAXIMO_DIAS;

    private final MensajeRepository mensajeRepository;
    private final CalificacionRepository calificacionRepository;
    private final TemaConocimientoRepository temaConocimientoRepository;
    private final TicketSoporteRepository ticketSoporteRepository;
    private final ConversacionRepository conversacionRepository;

    public AnaliticaChatbotService(MensajeRepository mensajeRepository, CalificacionRepository calificacionRepository,
                                   TemaConocimientoRepository temaConocimientoRepository,
                                   TicketSoporteRepository ticketSoporteRepository,
                                   ConversacionRepository conversacionRepository) {
        this.mensajeRepository = mensajeRepository;
        this.calificacionRepository = calificacionRepository;
        this.temaConocimientoRepository = temaConocimientoRepository;
        this.ticketSoporteRepository = ticketSoporteRepository;
        this.conversacionRepository = conversacionRepository;
    }

    // 'desde' y 'hasta' son dias completos e inclusivos en 'zona'.
    @Transactional(readOnly = true)
    public AnaliticaChatbot calcular(LocalDate desde, LocalDate hasta, ZoneId zona) {
        PeriodoDelPanel.validar(desde, hasta);
        Instant inicio = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();
        // 1.7.0: el periodo anterior de los mismos dias, para la tendencia de
        // las palabras clave.
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        Instant inicioAnterior = desde.minusDays(dias).atStartOfDay(zona).toInstant();

        List<RegistroDePregunta> preguntas = mensajeRepository.buscarPreguntasEntre(Remitente.USUARIO, inicio, fin);
        List<RegistroDeRespuesta> respuestas = mensajeRepository.buscarRespuestasMedidasEntre(Remitente.BOT, inicio, fin);
        List<Instant> iniciadas = conversacionRepository.buscarIniciosEntre(inicio, fin);
        List<Boolean> calificaciones = calificacionRepository.buscarUtilidadEntre(inicio, fin);
        List<RegistroDeTexto> textos = textosDePreguntasEntre(inicio, fin);
        List<RegistroDeTexto> textosAnteriores = textosDePreguntasEntre(inicioAnterior, inicio);

        long conversacionesConActividad = preguntas.stream().map(RegistroDePregunta::conversacionId).distinct().count();
        long escalamientos = respuestas.stream().filter(r -> Boolean.TRUE.equals(r.escalado())).count();
        long calificacionesUtiles = calificaciones.stream().filter(Boolean.TRUE::equals).count();
        List<RespuestasPorResultado> resultados = porResultado(respuestas);
        long clasificadas = resultados.stream().mapToLong(RespuestasPorResultado::respuestas).sum();
        long resueltas = resultados.stream().filter(r -> r.resultado().resuelve())
            .mapToLong(RespuestasPorResultado::respuestas).sum();

        return new AnaliticaChatbot(
            inicio,
            fin,
            zona.getId(),
            iniciadas.size(),
            conversacionesConActividad,
            preguntas.size(),
            respuestas.size(),
            escalamientos,
            clasificadas,
            resueltas,
            proporcion(resueltas, clasificadas),
            resultados,
            tiempoPromedio(respuestas),
            calificaciones.size(),
            calificacionesUtiles,
            proporcion(calificacionesUtiles, calificaciones.size()),
            temasFrecuentes(inicio, fin),
            PreguntasFrecuentes.contar(textos, TOP_PREGUNTAS),
            tendencia(desde, hasta, zona, iniciadas, preguntas, respuestas),
            AnaliticaDeTickets.resumir(ticketSoporteRepository.buscarCreadosEntre(inicio, fin)),
            PalabrasClaveFrecuentes.contar(textos, textosAnteriores, TOP_PALABRAS)
        );
    }

    private List<RegistroDeTexto> textosDePreguntasEntre(Instant inicio, Instant fin) {
        return mensajeRepository.buscarTextosDePreguntasEntre(Remitente.USUARIO, inicio, fin,
            PageRequest.of(0, MAXIMO_PREGUNTAS_PARA_PALABRAS));
    }

    // 1.7.0: cuantas respuestas de cada resultado, en el orden del enum y con
    // los que no ocurrieron en 0. Las que no tienen resultado (anteriores a
    // V13) no son de ninguno.
    private static List<RespuestasPorResultado> porResultado(List<RegistroDeRespuesta> respuestas) {
        Map<ResultadoDeRespuesta, Long> conteo = new EnumMap<>(ResultadoDeRespuesta.class);
        for (RegistroDeRespuesta respuesta : respuestas) {
            if (respuesta.resultado() != null) {
                conteo.merge(respuesta.resultado(), 1L, Long::sum);
            }
        }
        return Arrays.stream(ResultadoDeRespuesta.values())
            .map(resultado -> new RespuestasPorResultado(resultado, conteo.getOrDefault(resultado, 0L)))
            .toList();
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

    private List<PuntoDeTendencia> tendencia(LocalDate desde, LocalDate hasta, ZoneId zona, List<Instant> iniciadas,
                                             List<RegistroDePregunta> preguntas,
                                             List<RegistroDeRespuesta> respuestas) {
        // 1.7.0: las conversaciones de cada dia son las que empezaron ese dia.
        Map<LocalDate, Long> conversacionesPorDia = new HashMap<>();
        for (Instant inicio : iniciadas) {
            conversacionesPorDia.merge(LocalDate.ofInstant(inicio, zona), 1L, Long::sum);
        }

        Map<LocalDate, Long> preguntasPorDia = new HashMap<>();
        for (RegistroDePregunta pregunta : preguntas) {
            preguntasPorDia.merge(LocalDate.ofInstant(pregunta.fechaEnvio(), zona), 1L, Long::sum);
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
                conversacionesPorDia.getOrDefault(dia, 0L),
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
