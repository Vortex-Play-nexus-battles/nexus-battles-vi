package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

// HU-CHA-012 (RF-CHA-013): crear o editar un tema de la version candidata.
//   categoria  = la "intencion" que atiende el tema.
//   variantes  = frases con las que un usuario haria esa pregunta; al menos
//                una en espanol. Ninguna puede llevar ',' (ver VariantesDePregunta).
//   prioridad  = desempate cuando dos temas empatan en puntaje (mayor gana).
//   activo     = null se toma como true.
public record TemaConocimientoRequest(
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
}
