package com.nexusbattles.plataforma.salaspartidas.configuracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Degradacion controlada — HU-DIS-003, aplicada a las tres llamadas salientes
 * de este servicio.
 *
 * <p>Un corta circuitos <b>por dependencia</b>, nunca uno compartido: que el
 * motor de combate este caido no tiene por que cerrar la puerta del inventario,
 * y al reves. Cada uno protege a ESTE servicio, no al que esta caido: sin el,
 * cada peticion consumiria un hilo y un tiempo de espera contra un servicio
 * que no contesta, y con suficiente trafico el que se queda sin hilos es
 * salas-partidas entero. Ahi es donde la caida de un microservicio se
 * convierte en la caida de la aplicacion, que es lo que la HU existe para
 * evitar.
 *
 * <p><b>Dependencia y seccion.</b> La dependencia es el nombre interno, para la
 * bitacora y el panel de {@code GET /api/v1/degradacion} de metricas-plataforma.
 * La seccion es lo que lee el jugador en el aviso («Inventario no disponible
 * temporalmente», el ejemplo literal de la HU): dice QUE funcion queda limitada,
 * que es lo que exige CA-02. Se nombra la funcion, no el servicio, salvo donde
 * la HU ya fijo el texto.
 *
 * <p>Los umbrales bajan de {@code resiliencia.*} en {@code application.yml} y de
 * ahi de variables de entorno (regla 10): el E2E los acorta para observar la
 * recuperacion sin esperar medio minuto.
 */
@Configuration
public class ConfiguracionDeResiliencia {

    /** Nombres internos, para la bitacora y el registro de degradaciones. */
    public static final String INVENTARIO = "inventario";
    public static final String MOTOR_COMBATE = "motor-combate";
    public static final String MS_FINANZAS = "ms-finanzas";

    /** Lo que lee el jugador: la funcion que queda limitada (HU-DIS-003, CA-02). */
    public static final String SECCION_INVENTARIO = "Inventario";
    public static final String SECCION_COMBATE = "Motor de combate";
    public static final String SECCION_APUESTAS = "Apuesta de creditos";

    @Bean
    public CortaCircuitos cortaInventario(Umbrales umbrales, RegistroDeDegradacion registro) {
        return umbrales.para(INVENTARIO, SECCION_INVENTARIO, registro);
    }

    @Bean
    public CortaCircuitos cortaMotorCombate(Umbrales umbrales, RegistroDeDegradacion registro) {
        return umbrales.para(MOTOR_COMBATE, SECCION_COMBATE, registro);
    }

    @Bean
    public CortaCircuitos cortaCreditos(Umbrales umbrales, RegistroDeDegradacion registro) {
        return umbrales.para(MS_FINANZAS, SECCION_APUESTAS, registro);
    }

    /**
     * Los mismos umbrales para las tres dependencias.
     *
     * <p>{@code reintentar-en-segundos} es a la vez lo que dura abierto el
     * circuito y lo que se le promete al cliente en {@code Retry-After}: una
     * sola cifra, porque decirle al jugador «vuelve en 30 s» mientras el
     * circuito reintenta a los 10 seria una promesa distinta de la que se cumple.
     */
    @Bean
    public Umbrales umbralesDeResiliencia(
            @Value("${resiliencia.fallos-para-abrir:3}") int fallosParaAbrir,
            @Value("${resiliencia.reintentar-en-segundos:30}") long reintentarEnSegundos) {
        return new Umbrales(fallosParaAbrir, Duration.ofSeconds(reintentarEnSegundos));
    }

    public record Umbrales(int fallosParaAbrir, Duration esperaAntesDeReintentar) {

        CortaCircuitos para(String dependencia, String seccion, RegistroDeDegradacion registro) {
            return new CortaCircuitos(dependencia, seccion, fallosParaAbrir,
                    esperaAntesDeReintentar, Clock.systemUTC(), registro);
        }
    }
}
