package com.nexusbattles.plataforma.torneos.torneo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El arbol de RF-TOR-004 con la numeracion de la ficha: ganadores 1-6, 11 y
 * final; secundarios 7-10, 12 y 13. Doble eliminacion.
 */
@DisplayName("Arbol de ocho equipos (HU-TOR-004)")
class ArbolTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private final UUID torneo = UUID.randomUUID();
    private final List<Equipo> equipos = new ArrayList<>();
    private final Map<UUID, Equipo> porId = new HashMap<>();
    private List<Encuentro> arbol;

    @BeforeEach
    void ochoEquipos() {
        for (int p = 1; p <= 8; p++) {
            Equipo e = Equipo.deLaMaquina(UUID.randomUUID(), torneo, p, p, AHORA);
            equipos.add(e);
            porId.put(e.id(), e);
        }
        arbol = Arbol.generar(torneo, equipos);
    }

    private Encuentro encuentro(int numero) {
        return arbol.stream().filter(e -> e.numero() == numero).findFirst().orElseThrow();
    }

    private UUID equipo(int posicion) {
        return equipos.get(posicion - 1).id();
    }

    private Arbol.Movimiento gana(int numero, UUID ganador) {
        return Arbol.aplicar(arbol, porId, numero, ganador, null, "e2e", null, AHORA);
    }

    @Test
    @DisplayName("nacen 14 encuentros; los cuatro de la primera ronda quedan listos por posicion, el resto pendiente")
    void generar() {
        assertThat(arbol).hasSize(14);
        assertThat(encuentro(1).equipoA()).isEqualTo(equipo(1));
        assertThat(encuentro(1).equipoB()).isEqualTo(equipo(2));
        assertThat(encuentro(4).equipoA()).isEqualTo(equipo(7));
        assertThat(encuentro(4).equipoB()).isEqualTo(equipo(8));
        assertThat(arbol.stream().filter(Encuentro::listo).map(Encuentro::numero)).containsExactly(1, 2, 3, 4);
        assertThat(encuentro(11).llave()).isEqualTo(Encuentro.Llave.GANADORES);
        assertThat(encuentro(13).llave()).isEqualTo(Encuentro.Llave.SECUNDARIOS);
        assertThat(encuentro(14).llave()).isEqualTo(Encuentro.Llave.FINAL);
        assertThatThrownBy(() -> Arbol.generar(torneo, equipos.subList(0, 7)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el ganador de 1 va al 5 y el perdedor al 7 (primera derrota, sigue vivo)")
    void primeraRonda() {
        gana(1, equipo(1));
        assertThat(encuentro(1).estado()).isEqualTo(Encuentro.Estado.JUGADO);
        assertThat(encuentro(5).equipoA()).isEqualTo(equipo(1));
        assertThat(encuentro(7).equipoA()).isEqualTo(equipo(2));
        assertThat(porId.get(equipo(2)).derrotas()).isEqualTo(1);
        assertThat(porId.get(equipo(2)).eliminado()).isFalse();
        assertThat(encuentro(5).listo()).isFalse();
        gana(2, equipo(3));
        assertThat(encuentro(5).listo()).isTrue();
        assertThat(encuentro(7).listo()).isTrue();
    }

    @Test
    @DisplayName("un encuentro pendiente o ya jugado no se registra; el ganador tiene que jugar ahi")
    void rechazos() {
        assertThatThrownBy(() -> gana(5, equipo(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> gana(1, equipo(3))).isInstanceOf(IllegalArgumentException.class);
        gana(1, equipo(1));
        assertThatThrownBy(() -> gana(1, equipo(1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> gana(99, equipo(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("torneo completo: segunda derrota elimina y la final da campeon; el campeon puede venir de secundarios")
    void torneoCompleto() {
        // Primera ronda: ganan las posiciones impares.
        gana(1, equipo(1));
        gana(2, equipo(3));
        gana(3, equipo(5));
        gana(4, equipo(7));
        // Secundarios ronda 1: 2 vs 4 y 6 vs 8.
        assertThat(encuentro(7).equipoA()).isEqualTo(equipo(2));
        assertThat(encuentro(7).equipoB()).isEqualTo(equipo(4));
        gana(7, equipo(2));
        gana(8, equipo(6));
        assertThat(porId.get(equipo(4)).eliminado()).isTrue();
        assertThat(porId.get(equipo(8)).eliminado()).isTrue();
        // Ganadores ronda 2.
        gana(5, equipo(1));   // pierde 3 → va al 10 (B)
        gana(6, equipo(5));   // pierde 7 → va al 9 (B)
        assertThat(encuentro(9).equipoA()).isEqualTo(equipo(2));
        assertThat(encuentro(9).equipoB()).isEqualTo(equipo(7));
        assertThat(encuentro(10).equipoA()).isEqualTo(equipo(6));
        assertThat(encuentro(10).equipoB()).isEqualTo(equipo(3));
        gana(9, equipo(7));
        gana(10, equipo(3));
        assertThat(porId.get(equipo(2)).eliminado()).isTrue();
        // Final de ganadores: 1 vs 5.
        assertThat(encuentro(11).equipoA()).isEqualTo(equipo(1));
        assertThat(encuentro(11).equipoB()).isEqualTo(equipo(5));
        gana(11, equipo(1));  // 5 baja al 13 (B)
        gana(12, equipo(3));  // 7 eliminado
        assertThat(encuentro(13).equipoA()).isEqualTo(equipo(3));
        assertThat(encuentro(13).equipoB()).isEqualTo(equipo(5));
        gana(13, equipo(3));  // 5 eliminado (segunda derrota)
        assertThat(porId.get(equipo(5)).eliminado()).isTrue();
        assertThat(encuentro(14).equipoA()).isEqualTo(equipo(1));
        assertThat(encuentro(14).equipoB()).isEqualTo(equipo(3));
        Arbol.Movimiento fin = gana(14, equipo(3));
        assertThat(fin.campeon()).contains(equipo(3));
        assertThat(porId.get(equipo(1)).eliminado()).isTrue();
        assertThat(arbol.stream().allMatch(e -> e.estado() == Encuentro.Estado.JUGADO)).isTrue();
        // El campeon perdio una vez (en la 5) y el subcampeon tambien (en la final).
        assertThat(porId.get(equipo(3)).derrotas()).isEqualTo(1);
    }
}
