package com.nexusbattles.ms_chatbot.chat.sugerencias;

import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.model.Remitente;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.MotorRespuestas;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.repository.MensajeRepository;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ms-chatbot.yaml 1.15.0 (§7.4.3 «Sugerencias proactivas: recomendaciones
 * basadas en el comportamiento del usuario»; RF-CHA-006): lo que el asistente
 * le propone a quien abre la ventana, antes de que pregunte.
 *
 * <p>El comportamiento que se mira es solo el suyo con el asistente: los
 * temas de la base que ya le respondieron, en su propia conversación (la
 * clave sale de su identidad verificada), y la sección donde está. Se calcula
 * al pedirlo y no se guarda nada: ni un evento, ni un perfil, ni nada de otro
 * servicio (D-CHA-14, convención provisional). Cada sugerencia dice por qué
 * sale, para que no parezca adivinación:
 *
 * <ol>
 *   <li>{@link MotivoDeSugerencia#POR_LO_QUE_PREGUNTASTE}: de los temas que
 *       le respondieron, del más reciente hacia atrás, otros de su misma
 *       categoría que aún no le respondieron (dos por tema como mucho);</li>
 *   <li>{@link MotivoDeSugerencia#POR_DONDE_ESTAS}: los de la categoría de la
 *       sección donde está;</li>
 *   <li>{@link MotivoDeSugerencia#PARA_EMPEZAR}: solo a quien aún no preguntó
 *       nada, los más consultados por todos (los mismos que las preguntas
 *       frecuentes).</li>
 * </ol>
 *
 * <p>Nunca un tema de cortesía, ni uno que ya le respondieron. Todo sale de
 * la versión en producción: pulsar una sugerencia siempre tiene respuesta.
 */
@Service
public class SugerenciasParaTiService {

    static final int LIMITE_POR_OMISION = 3;
    static final int LIMITE_MAXIMO = 5;
    /** Cuántas respuestas suyas, de las más recientes, se miran. */
    static final int RESPUESTAS_QUE_SE_MIRAN = 50;
    /** Cuántas sugerencias como mucho por cada tema que le respondieron. */
    static final int POR_TEMA = 2;

    private final TemaConocimientoRepository temas;
    private final MensajeRepository mensajes;
    private final SugerenciasService frecuentes;

    public SugerenciasParaTiService(TemaConocimientoRepository temas, MensajeRepository mensajes,
                                    SugerenciasService frecuentes) {
        this.temas = temas;
        this.mensajes = mensajes;
        this.frecuentes = frecuentes;
    }

    /** Una sugerencia y por qué sale; {@code relacionadoCon} es el tema que la trajo, si lo hay. */
    public record SugerenciaParaTi(TemaConocimiento tema, MotivoDeSugerencia motivo, String relacionadoCon) {
    }

    @Transactional(readOnly = true)
    public List<SugerenciaParaTi> para(IdentidadDelChat identidad, VistaDelChat vista, Integer limite) {
        int cuantas = limite == null ? LIMITE_POR_OMISION : Math.clamp(limite, 1, LIMITE_MAXIMO);
        List<TemaConocimiento> enProduccion = temas.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION)
            .stream()
            .filter(tema -> !SugerenciasService.TITULOS_DE_CORTESIA
                .contains(NormalizadorTexto.normalizar(tema.getTitulo())))
            .sorted(MotorRespuestas.ORDEN_DE_TEMAS)
            .toList();
        Map<String, TemaConocimiento> porClave = enProduccion.stream()
            .collect(Collectors.toMap(TemaConocimiento::getClave, Function.identity(), (a, b) -> a));
        List<String> respondidos = respondidos(identidad);
        Set<String> yaVistos = new LinkedHashSet<>(respondidos);
        Map<String, SugerenciaParaTi> elegidas = new LinkedHashMap<>();

        for (String clave : respondidos) {
            TemaConocimiento consultado = porClave.get(clave);
            if (consultado != null) {
                agregar(elegidas, cuantas, POR_TEMA, yaVistos, deLaCategoria(enProduccion, consultado.getCategoria()),
                    MotivoDeSugerencia.POR_LO_QUE_PREGUNTASTE, consultado.getTitulo());
            }
        }
        Categoria deLaVista = vista == null ? null : vista.categoria();
        if (deLaVista != null) {
            agregar(elegidas, cuantas, cuantas, yaVistos, deLaCategoria(enProduccion, deLaVista),
                MotivoDeSugerencia.POR_DONDE_ESTAS, null);
        }
        if (respondidos.isEmpty()) {
            // Las preguntas frecuentes, solo si siguen en produccion y no son de cortesia.
            List<TemaConocimiento> masConsultados = frecuentes.sugerir(null, null, cuantas + LIMITE_MAXIMO).stream()
                .filter(tema -> porClave.containsKey(tema.getClave()))
                .toList();
            agregar(elegidas, cuantas, cuantas, yaVistos, masConsultados, MotivoDeSugerencia.PARA_EMPEZAR, null);
        }
        return List.copyOf(elegidas.values());
    }

    // Los temas que le respondieron, del mas reciente hacia atras, sin repetir.
    private List<String> respondidos(IdentidadDelChat identidad) {
        if (identidad == null) {
            return List.of();
        }
        return mensajes.temasQueLeRespondieron(identidad.claveDeConversacion(), Remitente.BOT,
                ResultadoDeRespuesta.TEMA, PageRequest.of(0, RESPUESTAS_QUE_SE_MIRAN))
            .stream()
            .distinct()
            .toList();
    }

    private static List<TemaConocimiento> deLaCategoria(List<TemaConocimiento> enProduccion, Categoria categoria) {
        return enProduccion.stream().filter(tema -> tema.atiende(categoria)).toList();
    }

    private static void agregar(Map<String, SugerenciaParaTi> elegidas, int cuantas, int deEstas,
                                Set<String> yaVistos, List<TemaConocimiento> candidatos,
                                MotivoDeSugerencia motivo, String relacionadoCon) {
        List<TemaConocimiento> nuevas = new ArrayList<>();
        for (TemaConocimiento tema : candidatos) {
            if (elegidas.size() + nuevas.size() >= cuantas || nuevas.size() >= deEstas) {
                break;
            }
            if (!yaVistos.contains(tema.getClave()) && !elegidas.containsKey(tema.getClave())) {
                nuevas.add(tema);
            }
        }
        for (TemaConocimiento tema : nuevas) {
            elegidas.put(tema.getClave(), new SugerenciaParaTi(tema, motivo, relacionadoCon));
        }
    }
}
