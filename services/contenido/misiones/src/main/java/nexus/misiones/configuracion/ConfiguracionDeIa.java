package nexus.misiones.configuracion;

import java.nio.file.Path;
import nexus.misiones.aplicacion.ServicioDeHeroes;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.ia.DecisorConModelo;
import nexus.misiones.ia.PuntuadorOnnx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La IA de combate con red neuronal propia (HU-SIM-008, RF-MOT-59): quien decide la jugada de cada turno.
 *
 * <p>Por omision es la regla de heroes de siempre. Con {@code misiones.ia.modelo.habilitado=true} y un modelo
 * valido en {@code misiones.ia.modelo.ruta}, el decisor es {@link DecisorConModelo}, que la envuelve (el modelo
 * propone y la regla acota). Si el modelo no esta, esta danado o no cuadra, el servicio arranca igual y decide la
 * regla: se registra en la bitacora, no se detiene nada.
 */
@Configuration
public class ConfiguracionDeIa {

    private static final Logger BITACORA = LoggerFactory.getLogger(ConfiguracionDeIa.class);

    /** El decisor de las simulaciones. Se llama aparte de {@code servicioDeHeroes}, que tambien es un decisor. */
    @Bean
    public DecisorDeTurno decisorDeTurnoConfigurado(ServicioDeHeroes heroes,
                                                    @Value("${misiones.ia.modelo.habilitado:false}") boolean habilitado,
                                                    @Value("${misiones.ia.modelo.ruta:}") String ruta,
                                                    @Value("${misiones.ia.modelo.confianza-minima:0.6}") double confianza) {
        return elegirDecisor(heroes, habilitado, ruta, confianza);
    }

    /** La regla, o la regla envuelta por el modelo si esta encendido y se pudo cargar. Nunca lanza por el modelo. */
    public static DecisorDeTurno elegirDecisor(DecisorDeTurno regla, boolean habilitado, String ruta,
                                               double confianzaMinima) {
        if (!habilitado) {
            return regla;
        }
        if (ruta == null || ruta.isBlank()) {
            BITACORA.warn("misiones.ia.modelo.habilitado=true pero misiones.ia.modelo.ruta esta vacia: "
                    + "las jugadas las decide la regla.");
            return regla;
        }
        try {
            PuntuadorOnnx modelo = PuntuadorOnnx.cargar(Path.of(ruta.trim()));
            try {
                DecisorDeTurno decisor = new DecisorConModelo(regla, modelo, confianzaMinima);
                BITACORA.info("IA de combate con modelo propio {} ({}): el modelo propone y la regla acota.",
                        modelo.version(), modelo.metadatos().sintetico()
                                ? "ENTRENADO CON DATOS SINTETICOS, solo para pruebas" : "entrenado con partidas");
                return decisor;
            } catch (RuntimeException e) {
                modelo.close();
                throw e;
            }
        } catch (RuntimeException | LinkageError e) {
            BITACORA.warn("No se pudo encender la IA con modelo ({}): las jugadas las decide la regla. {}", ruta,
                    e.getMessage());
            return regla;
        }
    }
}
