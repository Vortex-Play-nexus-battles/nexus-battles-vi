package com.nexusbattles.plataforma.observabilidad;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Bitacora en JSON hacia stdout en los veinte servicios — regla 6.
 *
 * <h2>El problema que resuelve</h2>
 *
 * La regla 6 dice «bitacora en JSON hacia stdout, nunca a archivo». Estaba
 * escrita desde agosto y la cumplian <b>dos servicios de veinte</b> —heroes y
 * productos—, cada uno con un formato distinto (uno {@code ecs}, el otro
 * {@code logstash}), y ninguno de los dos era de los que llevan el trace id
 * en el MDC. Resultado neto: el identificador de traza no salia en la
 * bitacora de <b>ningun</b> servicio. Se propagaba y no se podia leer.
 *
 * <h2>Por que un EnvironmentPostProcessor y no una linea en cada servicio</h2>
 *
 * El formato de bitacora se decide <b>antes</b> de que exista el contexto de
 * Spring: cuando una autoconfiguracion se ejecuta, el sistema de registro ya
 * esta montado. Asi que no se puede encender desde un {@code @Bean}.
 *
 * <p>Repartirlo copiando la propiedad en dieciocho {@code application.yml} de
 * tres equipos distintos tiene el problema de siempre: el servicio que nazca
 * manana nace sin ella, y nadie se entera hasta que hace falta leer un
 * incidente. Aqui se declara <b>una vez</b> en la biblioteca que las
 * convenciones de Gradle ya reparten a los veinte modulos.
 *
 * <h2>Es un valor por omision, no una imposicion</h2>
 *
 * La fuente de propiedades se anade al <b>final</b> de la lista, que es la de
 * menor precedencia. Cualquier cosa que ya diga algo sobre el formato gana:
 * el {@code application.properties} del servicio, una variable de entorno, un
 * argumento de linea de comandos. En particular:
 *
 * <ul>
 *   <li>heroes conserva su {@code ecs} y productos su {@code logstash}: lo
 *       tienen escrito y esto no lo pisa;</li>
 *   <li>en desarrollo local, {@code LOGGING_STRUCTURED_FORMAT_CONSOLE=} vacio
 *       devuelve el formato de texto legible de Spring Boot.</li>
 * </ul>
 *
 * <p>El formato elegido es <b>ECS</b> (Elastic Common Schema) porque es el
 * que ya usaba heroes, vuelca el MDC entero —y con el, el {@code trazaId} de
 * {@link com.nexusbattles.comun.observabilidad.FiltroDeTraza}— y nombra los
 * campos con un estandar publicado en vez de con uno propio.
 */
public class BitacoraEnJsonPorOmision implements EnvironmentPostProcessor {

    /** Nombre visible en el informe de configuracion, para que se sepa de donde sale. */
    static final String FUENTE = "bitacora-json-de-plataforma";

    static final String PROPIEDAD = "logging.structured.format.console";

    /**
     * ECS: el MDC completo sale como campos del documento, asi que el
     * {@code trazaId} aparece sin configurar un patron a mano.
     */
    static final String FORMATO = "ecs";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment entorno, SpringApplication aplicacion) {
        // addLast: la MENOR precedencia. Esto es un valor por omision para el
        // servicio que no ha dicho nada, no una decision impuesta a los que si.
        entorno.getPropertySources().addLast(
                new MapPropertySource(FUENTE, Map.of(PROPIEDAD, FORMATO)));
    }
}
