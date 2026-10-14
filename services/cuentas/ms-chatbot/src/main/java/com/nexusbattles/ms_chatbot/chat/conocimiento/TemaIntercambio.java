package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

// HU-CHA-012 (RF-CHA-013): un tema en el formato de importacion/exportacion.
// Mismos campos que TemaConocimientoRequest mas la 'clave' estable: al
// exportar siempre viene; al importar es opcional (sin clave, el tema se
// trata como nuevo). Conservarla es lo que permite que un tema exportado y
// vuelto a importar siga contando como el mismo en las analiticas.
//
// 1.6.0: 'intencionesAdicionales' siempre viene al exportar (lista vacia si no
// tiene); un archivo anterior a 1.6.0, sin el campo, se importa igual.
//
// 1.17.0: 'tituloEn', el titulo en ingles, opcional; un archivo anterior,
// sin el campo, se importa igual.
public record TemaIntercambio(
    @Size(max = 80) String clave,
    @NotNull Categoria categoria,
    List<Categoria> intencionesAdicionales,
    @NotNull TipoRespuesta tipoRespuesta,
    @NotBlank @Size(max = 150) String titulo,
    @Size(max = 150) String tituloEn,
    @NotEmpty List<@NotBlank @Size(max = 200) String> variantesEs,
    List<@NotBlank @Size(max = 200) String> variantesEn,
    @NotBlank @Size(max = 4000) String respuestaEs,
    @Size(max = 4000) String respuestaEn,
    @PositiveOrZero int prioridad,
    Boolean activo
) {

    /** Sin titulo en ingles, como antes de 1.17.0. */
    public TemaIntercambio(String clave, Categoria categoria, List<Categoria> intencionesAdicionales,
                           TipoRespuesta tipoRespuesta, String titulo, List<String> variantesEs,
                           List<String> variantesEn, String respuestaEs, String respuestaEn, int prioridad,
                           Boolean activo) {
        this(clave, categoria, intencionesAdicionales, tipoRespuesta, titulo, null, variantesEs, variantesEn,
            respuestaEs, respuestaEn, prioridad, activo);
    }

    public static TemaIntercambio desde(TemaConocimiento tema) {
        return new TemaIntercambio(
            tema.getClave(),
            tema.getCategoria(),
            tema.getIntencionesAdicionales(),
            tema.getTipoRespuesta(),
            tema.getTitulo(),
            tema.getTituloEn(),
            VariantesDePregunta.separar(tema.getPalabrasClaveEs()),
            VariantesDePregunta.separar(tema.getPalabrasClaveEn()),
            tema.getContenidoRespuestaEs(),
            tema.getContenidoRespuestaEn(),
            tema.getPrioridad(),
            tema.isActivo());
    }
}
