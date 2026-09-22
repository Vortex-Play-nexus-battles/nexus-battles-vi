package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Un libro de creditos en memoria para la recompensa por jugar — HU-JUE-012.
 *
 * <p>Se comporta como ms-finanzas para lo que estas pruebas necesitan: acredita
 * 2 al ganador de un uno contra uno, 4 a cada ganador de una grupal y 1 al
 * resto; excluye a los sancionados; y la segunda vez que ve el mismo
 * {@code idPartida} responde «ya procesada» en vez de acreditar otra vez. Se
 * guarda cada informe recibido para poder afirmar que se le conto.
 */
public class AcreditadorEnMemoria implements AcreditadorDePartidas {

    /** Informes recibidos, en orden (incluidos los que fallaron por caido). */
    final List<InformeDePartida> informes = new ArrayList<>();

    /** Partidas que ya proceso. */
    final Set<UUID> procesadas = new HashSet<>();

    /** Cuando esta caido, no responde. */
    boolean caido;

    /** Cuando rechaza, contesta un 4xx distinto de 409. */
    boolean rechaza;

    @Override
    public Acreditacion acreditar(InformeDePartida informe) {
        informes.add(informe);
        if (caido) {
            throw new com.nexusbattles.plataforma.resiliencia.DependenciaDegradada(
                    "creditos", "recompensa", new IllegalStateException("el libro no responde"));
        }
        if (rechaza) {
            throw new CreditosNoDisponibles("el libro rechazo el resultado con 400");
        }
        if (!procesadas.add(informe.idPartida())) {
            return Acreditacion.repetida();
        }
        List<CreditoPorPartida> creditos = new ArrayList<>();
        List<UUID> excluidos = new ArrayList<>();
        int porGanar = informe.tipo() == TipoDePartida.GRUPAL ? 4 : 2;
        for (InformeDePartida.Jugador jugador : informe.jugadores()) {
            if (jugador.sancionado()) {
                excluidos.add(jugador.id());
                continue;
            }
            boolean gano = informe.ganadores().contains(jugador.id());
            creditos.add(new CreditoPorPartida(jugador.id(), gano ? porGanar : 1, gano, null));
        }
        return new Acreditacion(creditos, excluidos, false);
    }
}
