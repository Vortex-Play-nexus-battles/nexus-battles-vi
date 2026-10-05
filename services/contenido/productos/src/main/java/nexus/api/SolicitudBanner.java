package nexus.api;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SolicitudBanner(
        @NotBlank(message = "el contenido es obligatorio")
        @Size(max = 500, message = "el contenido no puede superar 500 caracteres")
        String contenido,
        @NotNull(message = "la fecha de publicacion es obligatoria")
        Instant publicarDesde,
        @NotNull(message = "la fecha final de vigencia es obligatoria")
        @Future(message = "la fecha final de vigencia debe estar en el futuro")
        Instant vigenteHasta) {

        @AssertTrue(message = "la vigencia debe terminar despues de la fecha de publicacion")
        public boolean isPeriodoValido() {
                return publicarDesde == null
                        || vigenteHasta == null
                        || vigenteHasta.isAfter(publicarDesde);
        }
}
