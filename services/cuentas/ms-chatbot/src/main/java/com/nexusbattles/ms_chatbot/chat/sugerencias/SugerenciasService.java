package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.analitica.ConteoDeTema;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// ms-chatbot.yaml 1.3.3 (7.4.2 «sugerencias de preguntas relacionadas»,
// 7.4.6 «preguntas rapidas», «menu de temas frecuentes» y «auto-completado»).
//
// Todo sale de la version EN PRODUCCION de la base de conocimiento: cada
// sugerencia es una pregunta que el asistente sabe responder. No depende de
// quien pregunta.
//
//   * Sin texto: los temas mas consultados de los ultimos 30 dias (respuestas
//     del bot por tema); a igual numero, el de mayor prioridad y luego por
//     titulo. Un tema sin consultas tambien sale, detras, para que el menu no
//     quede vacio en una base recien desplegada.
//   * Con texto (2 o mas caracteres): autocompletado. Primero los titulos que
//     EMPIEZAN por lo escrito, luego los que lo contienen, luego los temas con
//     una variante de pregunta que lo contiene; sin tildes ni mayusculas.
//
// Los temas de cortesia (saludo, despedida) no se sugieren: nadie necesita un
// boton para decir "hola".
@Service
public class SugerenciasService {

    static final int LIMITE_POR_OMISION = 6;
    static final int LIMITE_MAXIMO = 10;
    static final int MINIMO_PARA_AUTOCOMPLETAR = 2;
    static final Duration VENTANA_DE_FRECUENCIA = Duration.ofDays(30);
    static final Set<String> TITULOS_DE_CORTESIA = Set.of("saludo", "despedida");

    private final TemaConocimientoRepository temas;
    private final MensajeRepository mensajes;
    private final Clock reloj;

    public SugerenciasService(TemaConocimientoRepository temas, MensajeRepository mensajes, Clock reloj) {
        this.temas = temas;
        this.mensajes = mensajes;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<TemaConocimiento> sugerir(String texto, Categoria categoria, Integer limite) {
        int cuantos = limite == null ? LIMITE_POR_OMISION : Math.clamp(limite, 1, LIMITE_MAXIMO);
        List<TemaConocimiento> candidatos = temas.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION)
            .stream()
            .filter(tema -> !TITULOS_DE_CORTESIA.contains(NormalizadorTexto.normalizar(tema.getTitulo())))
            .filter(tema -> categoria == null || categoria == tema.getCategoria())
            .toList();

        String buscado = NormalizadorTexto.normalizar(texto);
        Stream<TemaConocimiento> ordenados = buscado.length() >= MINIMO_PARA_AUTOCOMPLETAR
            ? autocompletar(candidatos, buscado)
            : masConsultados(candidatos);
        return ordenados.limit(cuantos).toList();
    }

    private Stream<TemaConocimiento> autocompletar(List<TemaConocimiento> candidatos, String buscado) {
        ToIntFunction<TemaConocimiento> relevancia = tema -> relevancia(tema, buscado);
        return candidatos.stream()
            .filter(tema -> relevancia.applyAsInt(tema) > 0)
            .sorted(Comparator.comparingInt(relevancia).reversed()
                .thenComparing(Comparator.comparingInt(TemaConocimiento::getPrioridad).reversed())
                .thenComparing(TemaConocimiento::getTitulo));
    }

    // 3: el titulo empieza por lo escrito; 2: lo contiene; 1: alguna variante
    // (en espanol o en ingles) lo contiene; 0: no aparece.
    static int relevancia(TemaConocimiento tema, String buscado) {
        String titulo = NormalizadorTexto.normalizar(tema.getTitulo());
        if (titulo.startsWith(buscado)) {
            return 3;
        }
        if (titulo.contains(buscado)) {
            return 2;
        }
        boolean enVariantes = Stream.of(tema.getPalabrasClaveEs(), tema.getPalabrasClaveEn())
            .filter(lista -> lista != null && !lista.isBlank())
            .flatMap(lista -> Arrays.stream(lista.split(",")))
            .map(NormalizadorTexto::normalizar)
            .anyMatch(variante -> variante.contains(buscado));
        return enVariantes ? 1 : 0;
    }

    private Stream<TemaConocimiento> masConsultados(List<TemaConocimiento> candidatos) {
        Instant hasta = reloj.instant();
        Map<String, Long> consultas = mensajes
            .contarRespuestasPorTemaEntre(Remitente.BOT, hasta.minus(VENTANA_DE_FRECUENCIA), hasta,
                Pageable.unpaged())
            .stream()
            .collect(Collectors.toMap(ConteoDeTema::temaClave, ConteoDeTema::respuestas, Long::sum));
        return candidatos.stream()
            .sorted(Comparator.<TemaConocimiento>comparingLong(tema -> consultas.getOrDefault(tema.getClave(), 0L))
                .reversed()
                .thenComparing(Comparator.comparingInt(TemaConocimiento::getPrioridad).reversed())
                .thenComparing(TemaConocimiento::getTitulo));
    }
}
