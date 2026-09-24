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
public record TemaIntercambio(
    @Size(max = 80) String clave,
    @NotNull Categoria categoria,
    @NotNull TipoRespuesta tipoRespuesta,
    @NotBlank @Size(max = 150) String titulo,
    @NotEmpty List<@NotBlank @Size(max = 200) String> variantesEs,
    List<@NotBlank @Size(max = 200) String> variantesEn,
    @NotBlank @Size(max = 4000) String respuestaEs,
    @Size(max = 4000) String respuestaEn,
    @PositiveOrZero int prioridad,
    Boolean activo
) {

    public static TemaIntercambio desde(TemaConocimiento tema) {
        return new TemaIntercambio(
            tema.getClave(),
            tema.getCategoria(),
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
