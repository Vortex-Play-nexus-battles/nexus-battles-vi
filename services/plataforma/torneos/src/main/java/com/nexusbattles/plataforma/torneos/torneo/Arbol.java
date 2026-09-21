package com.nexusbattles.plataforma.torneos.torneo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El arbol de ocho equipos de RF-TOR-004, con la numeracion de la ficha:
 *
 * <pre>
 *  Ganadores:   1 2 3 4  →  5 6  →  11  →  final (14)
 *  Secundarios: 7 8 (perdedores de 1-4) → 9 10 (contra perdedores de 5-6) → 12 → 13 (contra perdedor de 11)
 * </pre>
 *
 * <p>Doble eliminacion: el ganador avanza, el perdedor baja a secundarios y a
 * la segunda derrota queda eliminado. Sin «bracket reset»: la final es un
 * solo encuentro entre el ganador de 11 y el de 13 (la ficha habla de «la
 * final», en singular).
 *
 * <p>Logica pura sobre las entidades: no sabe de base de datos ni de HTTP.
 */
public final class Arbol {

    /** A donde va el ganador (y el perdedor) de cada encuentro. */
    record Destino(int numero, boolean enA) { }

    /** Regla de un encuentro: llave, ronda, destino del ganador y del perdedor (null = eliminado). */
    record Regla(int numero, Encuentro.Llave llave, int ronda, Destino ganadorVa, Destino perdedorVa) { }

    static final List<Regla> REGLAS = List.of(
            new Regla(1, Encuentro.Llave.GANADORES, 1, new Destino(5, true), new Destino(7, true)),
            new Regla(2, Encuentro.Llave.GANADORES, 1, new Destino(5, false), new Destino(7, false)),
            new Regla(3, Encuentro.Llave.GANADORES, 1, new Destino(6, true), new Destino(8, true)),
            new Regla(4, Encuentro.Llave.GANADORES, 1, new Destino(6, false), new Destino(8, false)),
            new Regla(5, Encuentro.Llave.GANADORES, 2, new Destino(11, true), new Destino(10, false)),
            new Regla(6, Encuentro.Llave.GANADORES, 2, new Destino(11, false), new Destino(9, false)),
            new Regla(7, Encuentro.Llave.SECUNDARIOS, 1, new Destino(9, true), null),
            new Regla(8, Encuentro.Llave.SECUNDARIOS, 1, new Destino(10, true), null),
            new Regla(9, Encuentro.Llave.SECUNDARIOS, 2, new Destino(12, true), null),
            new Regla(10, Encuentro.Llave.SECUNDARIOS, 2, new Destino(12, false), null),
            new Regla(11, Encuentro.Llave.GANADORES, 3, new Destino(Encuentro.FINAL, true), new Destino(13, false)),
            new Regla(12, Encuentro.Llave.SECUNDARIOS, 3, new Destino(13, true), null),
            new Regla(13, Encuentro.Llave.SECUNDARIOS, 4, new Destino(Encuentro.FINAL, false), null),
            new Regla(Encuentro.FINAL, Encuentro.Llave.FINAL, 4, null, null));

    private Arbol() {
    }

    static Regla reglaDe(int numero) {
        return REGLAS.stream().filter(r -> r.numero() == numero).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no existe el encuentro " + numero));
    }

    /**
     * Los 14 encuentros de un torneo con las ocho posiciones ya sentadas en la
     * primera ronda: 1 vs 2, 3 vs 4, 5 vs 6, 7 vs 8 (por posicion de inscripcion).
     *
     * @param equiposPorPosicion ocho equipos, indice 0 = posicion 1
     */
    public static List<Encuentro> generar(UUID torneoId, List<Equipo> equiposPorPosicion) {
        if (equiposPorPosicion.size() != Torneo.CUPOS) {
            throw new IllegalArgumentException("el arbol necesita exactamente " + Torneo.CUPOS + " equipos");
        }
        List<Encuentro> encuentros = new ArrayList<>();
        for (Regla regla : REGLAS) {
            encuentros.add(new Encuentro(UUID.randomUUID(), torneoId, regla.numero(), regla.llave(), regla.ronda()));
        }
        for (int i = 0; i < 4; i++) {
            Encuentro primeraRonda = encuentros.get(i);
            primeraRonda.ponerEnA(equiposPorPosicion.get(2 * i).id());
            primeraRonda.ponerEnB(equiposPorPosicion.get(2 * i + 1).id());
        }
        return encuentros;
    }

    /** Resultado de aplicar un encuentro: quien queda campeon si fue la final. */
    public record Movimiento(Encuentro jugado, Optional<UUID> campeon) { }

    /**
     * Registra el ganador y mueve a los dos equipos por el arbol.
     *
     * @throws IllegalStateException    si el encuentro no esta listo o ya se jugo
     * @throws IllegalArgumentException si el ganador no juega ese encuentro
     */
    public static Movimiento aplicar(List<Encuentro> encuentros, Map<UUID, Equipo> equipos, int numero,
                                     UUID ganador, UUID partidaId, String registradoPor, String motivo,
                                     OffsetDateTime ahora) {
        Map<Integer, Encuentro> porNumero = encuentros.stream()
                .collect(Collectors.toMap(Encuentro::numero, Function.identity()));
        Encuentro encuentro = porNumero.get(numero);
        if (encuentro == null) {
            throw new IllegalArgumentException("no existe el encuentro " + numero);
        }
        encuentro.jugar(ganador, partidaId, registradoPor, motivo, ahora);
        UUID perdedor = encuentro.perdedor();
        Regla regla = reglaDe(numero);

        Equipo derrotado = equipos.get(perdedor);
        if (derrotado != null) {
            derrotado.anotarDerrota();
        }
        if (regla.ganadorVa() != null) {
            colocar(porNumero.get(regla.ganadorVa().numero()), regla.ganadorVa().enA(), ganador);
        }
        if (regla.perdedorVa() != null) {
            colocar(porNumero.get(regla.perdedorVa().numero()), regla.perdedorVa().enA(), perdedor);
        } else if (derrotado != null) {
            derrotado.eliminar();
        }
        return new Movimiento(encuentro, numero == Encuentro.FINAL ? Optional.of(ganador) : Optional.empty());
    }

    private static void colocar(Encuentro destino, boolean enA, UUID equipo) {
        if (enA) {
            destino.ponerEnA(equipo);
        } else {
            destino.ponerEnB(equipo);
        }
    }
}
