package com.nexusbattles.plataforma.correo.cola;

import java.time.Duration;
import java.util.List;

/**
 * Cuanto se espera antes de reintentar un envio y cuando se deja de intentar.
 *
 * <p>Espera creciente (30 s, 1 min, 2 min, 5 min, 15 min, 30 min, 1 h por
 * omision): un proveedor que tuvo un tropiezo se recupera en segundos y el
 * correo sale casi sin retraso; uno caido de verdad no recibe una rafaga de
 * reintentos que lo castigue a el y gaste la cuota diaria. Pasado el ultimo
 * valor se repite el ultimo.
 *
 * <p>Al agotar {@code maxIntentos} el envio queda FALLIDO: seguir para
 * siempre llenaria la cola de correos que ya nadie espera (un codigo de
 * recuperacion caduca en minutos).
 */
public final class PoliticaDeReintentos {

    private final List<Duration> esperas;
    private final int maxIntentos;

    public PoliticaDeReintentos(List<Duration> esperas, int maxIntentos) {
        if (esperas == null || esperas.isEmpty()) {
            throw new IllegalArgumentException("correo.entrega.esperas necesita al menos una espera");
        }
        for (Duration espera : esperas) {
            if (espera == null || espera.isNegative() || espera.isZero()) {
                throw new IllegalArgumentException("correo.entrega.esperas solo admite esperas positivas: " + esperas);
            }
        }
        if (maxIntentos < 1) {
            throw new IllegalArgumentException("correo.entrega.max-intentos debe ser al menos 1, y es " + maxIntentos);
        }
        this.esperas = List.copyOf(esperas);
        this.maxIntentos = maxIntentos;
    }

    /**
     * Espera antes del siguiente intento.
     *
     * @param intentosHechos intentos empezados hasta ahora (1 tras el primero)
     */
    public Duration esperaTras(int intentosHechos) {
        int indice = Math.max(0, Math.min(intentosHechos - 1, esperas.size() - 1));
        return esperas.get(indice);
    }

    /** True si con estos intentos ya no se intenta mas. */
    public boolean agotado(int intentosHechos) {
        return intentosHechos >= maxIntentos;
    }

    public int maxIntentos() {
        return maxIntentos;
    }
}
