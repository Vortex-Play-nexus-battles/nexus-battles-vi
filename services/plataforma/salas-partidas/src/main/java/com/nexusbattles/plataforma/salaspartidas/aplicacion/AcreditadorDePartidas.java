package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Puerto de salida hacia el libro de creditos para la recompensa por jugar —
 * RF-JUE-012, HU-JUE-012.
 *
 * <p>Este servicio <b>informa</b> el resultado de la partida; cuanto vale una
 * victoria (2, 4, 1) lo decide y lo acredita ms-finanzas
 * ({@code POST /partidas/resultado} de {@code contracts/openapi/creditos.yaml}),
 * que ademas entrega los cofres de HU-JUE-013. Aqui no hay cifras: se anuncia
 * lo que el libro contesto.
 *
 * <p>La operacion es idempotente por {@code idPartida} del lado del libro:
 * informar dos veces la misma partida responde {@code 409 partida-ya-procesada},
 * que el adaptador traduce a {@link Acreditacion#yaProcesada()} y no a un
 * fallo. Es lo que permite reintentar sin acreditar dos veces (CA-05).
 */
public interface AcreditadorDePartidas {

    /**
     * Informa el resultado y devuelve lo que el libro acredito.
     *
     * @throws CreditosNoDisponibles si el libro rechaza el informe (4xx que no
     *                               sea 409)
     * @throws RuntimeException      (una {@code DependenciaDegradada}) si el
     *                               libro no responde
     */
    Acreditacion acreditar(InformeDePartida informe);

    /** Como se clasifica la partida para el libro: es lo que fija la cifra. */
    enum TipoDePartida {
        /** Dos contendientes (incluida la modalidad contra la maquina). */
        UNO_A_UNO,
        /** Mas de dos, con o sin equipos. */
        GRUPAL
    }

    /**
     * Lo que se le cuenta al libro.
     *
     * @param idPartida clave de idempotencia del libro
     * @param tipo      uno contra uno o grupal
     * @param ganadores humanos que ganaron: uno, todo el equipo ganador, o
     *                  nadie (empate, o gano la maquina)
     * @param jugadores todos los humanos que jugaron, con su estado de sancion
     */
    record InformeDePartida(UUID idPartida, TipoDePartida tipo, List<UUID> ganadores,
                            List<Jugador> jugadores) {
        public InformeDePartida {
            Objects.requireNonNull(idPartida);
            Objects.requireNonNull(tipo);
            ganadores = List.copyOf(Objects.requireNonNull(ganadores));
            jugadores = List.copyOf(Objects.requireNonNull(jugadores));
            if (jugadores.isEmpty()) {
                throw new IllegalArgumentException("Sin humanos no hay a quien recompensar.");
            }
        }

        /** Un humano de la partida. Un sancionado se informa; el libro lo excluye (CA-04). */
        public record Jugador(UUID id, boolean sancionado) {
            public Jugador {
                Objects.requireNonNull(id);
            }
        }
    }

    /**
     * Lo que el libro acredito.
     *
     * @param creditos             una entrada por humano no sancionado
     * @param sancionadosExcluidos quienes se quedaron sin recompensa por sancion
     * @param yaProcesada          el libro ya tenia esta partida: no se acredito
     *                             nada nuevo y no hay detalle que anunciar
     */
    record Acreditacion(List<CreditoPorPartida> creditos, List<UUID> sancionadosExcluidos,
                        boolean yaProcesada) {
        public Acreditacion {
            creditos = List.copyOf(Objects.requireNonNull(creditos));
            sancionadosExcluidos = List.copyOf(Objects.requireNonNull(sancionadosExcluidos));
        }

        public static Acreditacion repetida() {
            return new Acreditacion(List.of(), List.of(), true);
        }
    }
}
