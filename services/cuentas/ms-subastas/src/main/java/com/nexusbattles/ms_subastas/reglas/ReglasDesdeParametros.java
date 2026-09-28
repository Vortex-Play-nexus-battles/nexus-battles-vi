package com.nexusbattles.ms_subastas.reglas;

import com.nexusbattles.ms_subastas.pujas.service.ParametrosPuja;
import com.nexusbattles.plataforma.resiliencia.parametros.LectorDeParametros;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Las reglas de 7.7 leidas del catalogo de admin-parametros (RF-ADM-001).
 *
 * <p><b>El incremento minimo no tiene respaldo, y es a proposito.</b> Hasta B8
 * salia de {@code SUBASTAS_INCREMENTO_MINIMO}, una variable que en DEV estaba
 * vacia: publicar respondia 503 con un mensaje de configuracion. El valor es
 * una decision del Product Owner (RF-SUB-002) y el catalogo ya tenia su clave,
 * {@code subastas.incremento-minimo}, sin valor. Ahora manda el catalogo: sin
 * valor ahi, no hay incremento, y publicar lo dice con un problem details
 * claro. Poner una cifra por variable de entorno seria decidir producto desde
 * un fichero de despliegue.
 *
 * <p>Los topes de participacion (10, 50, 5 s) si tienen respaldo: los fija el
 * documento y las variables de entorno los traen con esos mismos valores.
 * Que el catalogo no responda no los cambia.
 *
 * <p>La lectura nunca lanza: esa politica es la de {@link LectorDeParametros},
 * comun a los veinte modulos.
 */
public class ReglasDesdeParametros implements FuenteDeReglas {

    static final String INCREMENTO = "subastas.incremento-minimo";
    static final String MAX_SUBASTAS = "subastas.max-subastas-activas-por-jugador";
    static final String MAX_PUJAS = "subastas.max-pujas-activas-por-jugador";
    static final String INTERVALO = "subastas.intervalo-minimo-segundos";
    static final String AL_VENCER = "subastas.pendientes.al-vencer";

    private static final Logger log = LoggerFactory.getLogger(ReglasDesdeParametros.class);

    private final LectorDeParametros catalogo;
    private final ParametrosPuja respaldo;
    private final PoliticaAlVencer alVencerPorOmision;

    public ReglasDesdeParametros(LectorDeParametros catalogo, ParametrosPuja respaldo,
                                 PoliticaAlVencer alVencerPorOmision) {
        this.catalogo = Objects.requireNonNull(catalogo, "catalogo");
        this.respaldo = Objects.requireNonNull(respaldo, "respaldo");
        this.alVencerPorOmision = Objects.requireNonNull(alVencerPorOmision, "alVencerPorOmision");
    }

    @Override
    public ReglasVigentes vigentes() {
        return new ReglasVigentes(
                incremento().orElse(null),
                (int) catalogo.entero(MAX_SUBASTAS, respaldo.getMaxSubastasActivasPorJugador()),
                (int) catalogo.entero(MAX_PUJAS, respaldo.getMaxPujasActivasPorJugador()),
                (int) catalogo.entero(INTERVALO, respaldo.getIntervaloMinimoSegundos()),
                catalogo.opcion(AL_VENCER, PoliticaAlVencer.class, alVencerPorOmision));
    }

    /**
     * El incremento del catalogo si es un decimal positivo. Uno mal escrito
     * degrada igual que uno sin valor: a «no configurado», nunca a una cifra.
     */
    private Optional<BigDecimal> incremento() {
        return catalogo.texto(INCREMENTO).flatMap(texto -> {
            try {
                BigDecimal valor = new BigDecimal(texto.trim());
                if (valor.signum() > 0) {
                    return Optional.of(valor);
                }
                log.warn("El parametro {} vale {}, que no es positivo: se trata como sin configurar", INCREMENTO, texto);
            } catch (NumberFormatException noEsDecimal) {
                log.warn("El parametro {} vale '{}', que no es un decimal: se trata como sin configurar",
                        INCREMENTO, texto);
            }
            return Optional.empty();
        });
    }
}
