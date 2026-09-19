package com.nexusbattles.ms_finanzas.partidas;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Encapsula la lógica del cofre semanal (HU-JUE-012):
 *
 * <ul>
 *   <li>Suma créditos ganados al contador de la semana ISO del jugador.</li>
 *   <li>Al llegar a 20 créditos ganados en la semana, entrega un cofre y
 *       resetea el contador a 0 — así el jugador puede acumular otros 20 y
 *       optar a un segundo cofre.</li>
 *   <li>Máximo 2 cofres por semana; el tercero no se entrega aunque el
 *       jugador siga acumulando.</li>
 * </ul>
 *
 * <p>Semana: ISO 8601 (formato {@code YYYY-Www}, por ejemplo
 * {@code 2026-W38}), con lunes como primer día. El día exacto de reinicio
 * de la semana está pendiente de confirmación con el Product Owner
 * (SRS RF-JUE-013); hasta entonces, lunes ISO es el default.
 *
 * <p>Contenido del cofre: placeholder {@code COFRE_ESTANDAR_v1}. La
 * definición del contenido y las probabilidades quedan por confirmar con el
 * Product Owner (RF-JUE-013). Cuando lo definan, aquí se conecta el
 * generador de recompensas real.
 */
@Service
public class CofreService {

    /** Umbral en créditos ganados para entregar un cofre. */
    public static final int UMBRAL_CREDITOS_POR_COFRE = 20;

    /** Cofres máximos por semana ISO por jugador. */
    public static final int MAXIMO_COFRES_POR_SEMANA = 2;

    /** Contenido placeholder del cofre — se define en RF-JUE-013. */
    public static final String CONTENIDO_PLACEHOLDER = "COFRE_ESTANDAR_v1";

    private static final DateTimeFormatter FORMATO_SEMANA_ISO =
            DateTimeFormatter.ofPattern("YYYY-'W'ww");

    private final ContadorSemanalCofreRepository contadorRepositorio;
    private final CofreEntregadoRepository cofreRepositorio;
    private final Clock reloj;

    public CofreService(
            ContadorSemanalCofreRepository contadorRepositorio,
            CofreEntregadoRepository cofreRepositorio,
            Clock reloj) {
        this.contadorRepositorio = contadorRepositorio;
        this.cofreRepositorio = cofreRepositorio;
        this.reloj = reloj;
    }

    /**
     * Suma {@code creditos} al contador semanal del jugador. Si el contador
     * alcanza o supera el umbral y hay cuota disponible, entrega un cofre y
     * lo devuelve. En cualquier otro caso devuelve {@link Optional#empty()}.
     */
    public Optional<CofreEntregado> registrarCreditosGanados(String uidJugador, int creditos) {
        String semana = semanaActual();
        ContadorSemanalCofre contador = contadorRepositorio
                .findById(new ContadorSemanalCofreId(uidJugador, semana))
                .orElseGet(() -> new ContadorSemanalCofre(uidJugador, semana));

        contador.setCreditosGanados(contador.getCreditosGanados() + creditos);

        boolean debeEntregar = contador.getCreditosGanados() >= UMBRAL_CREDITOS_POR_COFRE
                && contador.getCofresEntregados() < MAXIMO_COFRES_POR_SEMANA;

        if (!debeEntregar) {
            contadorRepositorio.save(contador);
            return Optional.empty();
        }

        // Se resetea a 0 (no a "resto") por decisión de diseño: la HU habla
        // de "acumular 20", no de "cada 20 nuevos". Después del cofre, el
        // jugador arranca la próxima ronda desde cero.
        contador.setCreditosGanados(0);
        contador.setCofresEntregados(contador.getCofresEntregados() + 1);
        contadorRepositorio.save(contador);

        CofreEntregado cofre = new CofreEntregado();
        cofre.setId(UUID.randomUUID());
        cofre.setUidJugador(uidJugador);
        cofre.setSemanaIso(semana);
        cofre.setContenido(CONTENIDO_PLACEHOLDER);
        cofre.setEntregadoEn(Instant.now(reloj));
        return Optional.of(cofreRepositorio.save(cofre));
    }

    /** Semana ISO 8601 del instante actual del reloj inyectado. */
    String semanaActual() {
        return LocalDate.ofInstant(Instant.now(reloj), reloj.getZone())
                .format(FORMATO_SEMANA_ISO);
    }
}
