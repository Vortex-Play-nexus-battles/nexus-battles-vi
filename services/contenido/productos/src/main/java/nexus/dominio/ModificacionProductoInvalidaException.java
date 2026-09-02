package nexus.dominio;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;

public class ModificacionProductoInvalidaException extends RuntimeException {

        public ModificacionProductoInvalidaException(Set<? extends ConstraintViolation<?>> violaciones) {
                super(formatear(violaciones));
        }

        private static String formatear(Set<? extends ConstraintViolation<?>> violaciones) {
                return violaciones.stream()
                        .map(ConstraintViolation::getMessage)
                        .distinct()
                        .collect(Collectors.joining("; "));
        }
}
