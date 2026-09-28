package com.nexusbattles.ms_finanzas.partidas;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Créditos ganados que el jugador lleva acumulados hacia su próximo cofre —
 * §7.6 del documento: «Cuando un jugador logre acumular veinte (20) créditos
 * en juegos ganados recibirá un cofre ... y el contador se reinicia cada vez
 * que complete los créditos» (cofres.yaml 1.1.0, B7).
 *
 * <p><b>Uno por jugador, no por semana.</b> Lo que se acumula se conserva de
 * una semana a otra: la semana solo limita cuántos cofres se entregan (dos,
 * contados en {@code cofre_entregado}), no cuánto se acumula. El contador
 * semanal anterior ({@code contador_semanal_cofre}, V4) mezclaba además los
 * créditos por participar; ya no se escribe (V5).
 *
 * <p><b>Bloqueo optimista.</b> Dos resultados de partida del mismo jugador a
 * la vez no pueden sumar sobre la misma lectura: sin {@link Version} los dos
 * verían 18, los dos llegarían a 20 y habría dos cofres por una sola cuota.
 * La segunda escritura falla y {@link RegistroDeResultados} la repite sobre el
 * valor nuevo.
 */
@Entity
@Table(name = "contador_de_cofres")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContadorDeCofres {

    @Id
    @Column(name = "uid_jugador", length = 64, nullable = false)
    private String uidJugador;

    @Column(name = "creditos_ganados", nullable = false)
    private int creditosGanados;

    /** Nulo mientras no se ha guardado: así Spring Data sabe que es nuevo. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "actualizado_en")
    private Instant actualizadoEn;

    /** Contador a cero de un jugador que todavía no ha ganado nada. */
    public static ContadorDeCofres nuevo(String uidJugador) {
        ContadorDeCofres contador = new ContadorDeCofres();
        contador.uidJugador = uidJugador;
        contador.creditosGanados = 0;
        return contador;
    }

    /** Suma los créditos de una partida ganada. */
    public void sumar(int creditos, Instant ahora) {
        if (creditos < 0) {
            throw new IllegalArgumentException("Los créditos ganados no pueden ser negativos.");
        }
        this.creditosGanados += creditos;
        this.actualizadoEn = ahora;
    }

    /** Si ya se completó la cuota del cofre. */
    public boolean completaLaCuota(int cuota) {
        return creditosGanados >= cuota;
    }

    /**
     * Reinicia el contador tras completar la cuota — «el contador se reinicia
     * cada vez que complete los créditos». Con {@code conservarSobrante} lo
     * que pasó de la cuota (22 → 2) cuenta para el siguiente cofre; sin él,
     * vuelve a cero (D-B7-17).
     */
    public void reiniciar(int cuota, boolean conservarSobrante) {
        this.creditosGanados = conservarSobrante ? Math.max(0, creditosGanados - cuota) : 0;
    }
}
