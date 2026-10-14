package nexus.recursos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Los límites de cada clase de recurso, de la configuración (regla 10).
 * Valores por omisión y variables de entorno en {@code application.properties}.
 */
@Configuration
public class ConfiguracionDeRecursos {

    @Bean
    public LimitesDeRecursos limitesDeRecursos(
            @Value("${catalogo.recursos.imagen.bytes-maximos:2097152}") int imagenBytes,
            @Value("${catalogo.recursos.imagen.lado-maximo:4096}") int imagenLado,
            @Value("${catalogo.recursos.icono.bytes-maximos:262144}") int iconoBytes,
            @Value("${catalogo.recursos.icono.lado-maximo:512}") int iconoLado,
            @Value("${catalogo.recursos.animacion.bytes-maximos:4194304}") int animacionBytes,
            @Value("${catalogo.recursos.animacion.lado-maximo:1024}") int animacionLado) {
        return new LimitesDeRecursos(
                new LimitesDeRecursos.Limite(imagenBytes, imagenLado),
                new LimitesDeRecursos.Limite(iconoBytes, iconoLado),
                new LimitesDeRecursos.Limite(animacionBytes, animacionLado));
    }

    @Bean
    public ExaminadorDeRecursos examinadorDeRecursos(LimitesDeRecursos limites) {
        return new ExaminadorDeRecursos(limites);
    }
}
