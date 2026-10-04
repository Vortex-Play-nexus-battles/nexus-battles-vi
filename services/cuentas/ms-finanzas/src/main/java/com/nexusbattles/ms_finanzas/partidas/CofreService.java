package com.nexusbattles.ms_finanzas.partidas;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El cofre por créditos ganados — §7.6 del documento, cofres.yaml 1.1.0 (B7):
 * «Cuando un jugador logre acumular veinte (20) créditos en juegos ganados
 * recibirá un cofre con una recompensa aleatoria cada vez que se cumpla la
 * cuota. Este beneficio solo podrá obtenerse dos veces por semana y el
 * contador se reinicia cada vez que complete los créditos».
 *
 * <ul>
 *   <li>Suma SOLO créditos de partidas ganadas: quien llama
 *       ({@link AcreditacionPartidaService}) no le pasa los de participar.</li>
 *   <li>Cada vez que el contador llega a {@link ReglasDeCofres#CUOTA}, se
 *       reinicia —conservando el sobrante o no, D-B7-17— y, si en la semana
 *       ISO del jugador todavía no hay {@link ReglasDeCofres#MAXIMO_POR_SEMANA}
 *       cofres, se gana uno. Con el tope alcanzado la cuota se consume igual y
 *       no hay cofre: el documento dice que el contador se reinicia al
 *       completar los créditos, no al entregar el cofre (D-B7-17).</li>
 *   <li>El contenido se sortea al ganarlo con una semilla de
 *       {@code SecureRandom} que queda guardada con el cofre, sobre la tabla
 *       vigente (D-B7-18). La entrega al inventario la hace
 *       {@link EntregaDeCofres}, fuera de esta transacción.</li>
 * </ul>
 *
 * <p>El contador lleva bloqueo optimista: dos resultados simultáneos del mismo
 * jugador no pueden completar la cuota dos veces sobre la misma lectura.
 */
public class CofreService {

    private static final Logger BITACORA = LoggerFactory.getLogger(CofreService.class);

    private final ContadorDeCofresRepository contadores;
    private final CofreEntregadoRepository cofres;
    private final ReglasDeCofres reglas;
    private final Clock reloj;
    private final RandomGenerator semillas;

    /**
     * @param semillas de donde sale la semilla de cada sorteo: {@code SecureRandom}
     *                 en producción, uno con semilla fija en las pruebas
     */
    public CofreService(ContadorDeCofresRepository contadores, CofreEntregadoRepository cofres,
                        ReglasDeCofres reglas, Clock reloj, RandomGenerator semillas) {
        this.contadores = Objects.requireNonNull(contadores);
        this.cofres = Objects.requireNonNull(cofres);
        this.reglas = Objects.requireNonNull(reglas);
        this.reloj = Objects.requireNonNull(reloj);
        this.semillas = Objects.requireNonNull(semillas);
    }

    /**
     * Suma los créditos de una partida GANADA al contador del jugador y, si
     * completa la cuota con tope semanal disponible, le da un cofre.
     *
     * @param creditosGanados los de la victoria (2 en un uno contra uno, 4 en
     *                        una grupal); cero o menos no cuentan
     * @return el cofre ganado, ya guardado y pendiente de entrega; vacío si no
     *         tocaba
     */
    public Optional<CofreEntregado> registrarCreditosGanados(String uidJugador, int creditosGanados) {
        Objects.requireNonNull(uidJugador, "El cofre es de un jugador.");
        if (creditosGanados <= 0) {
            return Optional.empty();
        }
        Instant ahora = reloj.instant();
        ContadorDeCofres contador = contadores.findById(uidJugador)
                .orElseGet(() -> ContadorDeCofres.nuevo(uidJugador));
        contador.sumar(creditosGanados, ahora);

        if (!contador.completaLaCuota(ReglasDeCofres.CUOTA)) {
            contadores.save(contador);
            return Optional.empty();
        }
        contador.reiniciar(ReglasDeCofres.CUOTA, reglas.conservarSobrante());
        contadores.save(contador);

        String semana = reglas.semanaIso(ahora);
        long enLaSemana = cofres.countByUidJugadorAndSemanaIso(uidJugador, semana);
        if (enLaSemana >= ReglasDeCofres.MAXIMO_POR_SEMANA) {
            BITACORA.info("El jugador {} completo la cuota del cofre con el tope de la semana {} ya alcanzado: "
                    + "el contador se reinicia y no hay cofre (D-B7-17)", uidJugador, semana);
            return Optional.empty();
        }

        CofreEntregado cofre = CofreEntregado.sorteado(uidJugador, semana, ahora, semillas.nextLong(),
                reglas.tabla());
        return Optional.of(cofres.save(cofre));
    }
}
