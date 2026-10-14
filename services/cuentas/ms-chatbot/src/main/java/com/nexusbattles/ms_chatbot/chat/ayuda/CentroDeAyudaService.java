package com.nexusbattles.ms_chatbot.chat.ayuda;

import com.nexusbattles.ms_chatbot.chat.ayuda.CentroDeAyudaResponse.CategoriaDeAyuda;
import com.nexusbattles.ms_chatbot.chat.ayuda.CentroDeAyudaResponse.TemaDeAyuda;
import com.nexusbattles.ms_chatbot.chat.enriquecido.Enriquecedor;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ms-chatbot.yaml 1.8.0 (7.4.3 «enlaces a documentacion relevante»): el centro
// de ayuda, lo mismo que el asistente sabe responder, para leerlo sin chatear.
//
//   * Solo la version EN PRODUCCION y solo sus temas activos: lo que hoy
//     responde el asistente, ni la candidata ni lo retirado.
//   * Sin los temas de cortesia (saludo, despedida): no ayudan a nadie.
//   * Por categoria, en el orden de Categoria; dentro de cada una, la mayor
//     prioridad primero y despues por titulo, como las sugerencias.
//   * Los pasos salen igual que en la respuesta del chat (Enriquecedor), solo
//     de los temas paso a paso.
//
// No depende de quien pregunta: el controlador lo deja en cache publica.
@Service
public class CentroDeAyudaService {

    private static final Comparator<TemaConocimiento> ORDEN = Comparator
        .comparingInt(TemaConocimiento::getPrioridad).reversed()
        .thenComparing(TemaConocimiento::getTitulo);

    private final TemaConocimientoRepository temas;

    public CentroDeAyudaService(TemaConocimientoRepository temas) {
        this.temas = temas;
    }

    @Transactional(readOnly = true)
    public CentroDeAyudaResponse centro() {
        Map<Categoria, List<TemaConocimiento>> porCategoria = temas
            .findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION)
            .stream()
            .filter(tema -> !Enriquecedor.esDeCortesia(tema))
            .collect(Collectors.groupingBy(TemaConocimiento::getCategoria, () -> new EnumMap<>(Categoria.class),
                Collectors.toList()));
        List<CategoriaDeAyuda> categorias = porCategoria.entrySet().stream()
            .map(grupo -> new CategoriaDeAyuda(grupo.getKey(), Enriquecedor.anclaDe(grupo.getKey()),
                grupo.getValue().stream().sorted(ORDEN).map(CentroDeAyudaService::comoTema).toList()))
            .toList();
        return new CentroDeAyudaResponse(categorias);
    }

    static TemaDeAyuda comoTema(TemaConocimiento tema) {
        boolean pasoAPaso = tema.getTipoRespuesta() == TipoRespuesta.PASO_A_PASO;
        String respuestaEn = tema.getContenidoRespuestaEn() == null || tema.getContenidoRespuestaEn().isBlank()
            ? null
            : tema.getContenidoRespuestaEn();
        return new TemaDeAyuda(tema.getTitulo(), tema.getContenidoRespuestaEs(), respuestaEn,
            pasoAPaso ? Enriquecedor.pasosDe(tema.getContenidoRespuestaEs()) : List.of(),
            pasoAPaso && respuestaEn != null ? Enriquecedor.pasosDe(respuestaEn) : List.of());
    }
}
