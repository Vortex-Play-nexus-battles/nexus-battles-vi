package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;

import java.util.List;
import java.util.UUID;

// 1.6.0: `intencionesAdicionales` siempre viene, vacia si el tema no tiene.
public record TemaConocimientoResponse(
    UUID id,
    String clave,
    Categoria categoria,
    List<Categoria> intencionesAdicionales,
    TipoRespuesta tipoRespuesta,
    String titulo,
    List<String> variantesEs,
    List<String> variantesEn,
    String respuestaEs,
    String respuestaEn,
    int prioridad,
    boolean activo
) {

    public static TemaConocimientoResponse desde(TemaConocimiento tema) {
        return new TemaConocimientoResponse(
            tema.getId(),
            tema.getClave(),
            tema.getCategoria(),
            tema.getIntencionesAdicionales(),
            tema.getTipoRespuesta(),
            tema.getTitulo(),
            VariantesDePregunta.separar(tema.getPalabrasClaveEs()),
            VariantesDePregunta.separar(tema.getPalabrasClaveEn()),
            tema.getContenidoRespuestaEs(),
            tema.getContenidoRespuestaEn(),
            tema.getPrioridad(),
            tema.isActivo());
    }
}
