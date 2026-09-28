package com.nexusbattles.ms_subastas.reglas;

import com.nexusbattles.ms_subastas.pujas.service.ParametrosPuja;

import java.math.BigDecimal;

/**
 * De donde salen las reglas de 7.7 que decide administracion.
 *
 * <p>En produccion es {@link ReglasDesdeParametros} (admin-parametros, con las
 * variables de entorno como respaldo). {@link #fijas} existe para las
 * pruebas de los motores y para quien construye las piezas a mano: mismas
 * reglas siempre, sin catalogo detras.
 */
@FunctionalInterface
public interface FuenteDeReglas {

    ReglasVigentes vigentes();

    /** Los limites de {@code parametros} y ningun incremento configurado. */
    static FuenteDeReglas fijas(ParametrosPuja parametros) {
        return fijas(parametros, null);
    }

    /** Los limites de {@code parametros} y el incremento dado (nulo = sin configurar). */
    static FuenteDeReglas fijas(ParametrosPuja parametros, BigDecimal incremento) {
        return () -> new ReglasVigentes(incremento,
                parametros.getMaxSubastasActivasPorJugador(),
                parametros.getMaxPujasActivasPorJugador(),
                parametros.getIntervaloMinimoSegundos(),
                PoliticaAlVencer.ENTREGAR);
    }
}
