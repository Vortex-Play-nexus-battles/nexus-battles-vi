package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EjecutorAtaqueConBotinIntegracionTest {

    @Test
    @DisplayName("una resolucion de HU-JUE-003 que derrota al enemigo activa el botin")
    void integraLaDerrotaDePartidaConElBotin() {
        Escenario escenario = escenario(10, BigDecimal.valueOf(100));

        Optional<ResultadoBotin> resultado = escenario.ejecutor.ejecutar(
                "accion-final",
                escenario.partida,
                escenario.control,
                escenario.atacante,
                escenario.objetivo,
                new ResolucionAtaque.ConEfecto(CategoriaEfecto.CAUSAR_DANO, 4000, 10));

        assertFalse(escenario.partida.combatiente(escenario.objetivo.combatienteId()).participa());
        assertEquals(0, escenario.partida.combatiente(escenario.objetivo.combatienteId()).vida());
        assertEquals(EstadoResultadoBotin.OTORGADO, resultado.orElseThrow().estado());
        assertEquals(1, escenario.inventario.otorgados);
    }

    @Test
    @DisplayName("un ataque no letal conserva el combate y no consulta el botin")
    void noProcesaBotinMientrasElEnemigoSigueActivo() {
        Escenario escenario = escenario(20, BigDecimal.valueOf(100));

        Optional<ResultadoBotin> resultado = escenario.ejecutor.ejecutar(
                "accion-no-letal",
                escenario.partida,
                escenario.control,
                escenario.atacante,
                escenario.objetivo,
                new ResolucionAtaque.ConEfecto(CategoriaEfecto.CAUSAR_DANO, 4000, 3));

        assertTrue(resultado.isEmpty());
        assertTrue(escenario.partida.combatiente(escenario.objetivo.combatienteId()).participa());
        assertFalse(escenario.partida.finalizada());
        assertEquals(0, escenario.inventario.consultas);
        assertEquals(0, escenario.inventario.otorgados);
    }

    private static Escenario escenario(int vidaObjetivo, BigDecimal tasa) {
        List<String> participantes = List.of("combatiente-a", "combatiente-b");
        ColaTurnos cola = ColaTurnos.sortear(participantes, new Random(29));
        ControlAccionesTurno control = ControlAccionesTurno.iniciar(
                cola,
                Duration.ofMinutes(1));
        String atacanteId = control.participanteActivo();
        String objetivoId = atacanteId.equals("combatiente-a")
                ? "combatiente-b"
                : "combatiente-a";
        Partida partida = Partida.iniciar(List.of(
                Combatiente.nuevo(atacanteId, "equipo-atacante", 20),
                Combatiente.nuevo(objetivoId, "equipo-objetivo", vidaObjetivo)));
        ParticipanteBotin atacante = new ParticipanteBotin(
                atacanteId,
                "jugador-atacante",
                "heroe-atacante");
        ParticipanteBotin objetivo = new ParticipanteBotin(
                objetivoId,
                "jugador-objetivo",
                "heroe-objetivo");
        InventarioPrueba inventario = new InventarioPrueba();
        ProductoBotin producto = new ProductoBotin(
                "producto-item",
                "Pocion",
                TipoBotin.ITEM,
                null,
                tasa);
        ProcesadorBotin procesador = new ProcesadorBotin(
                inventario,
                productoId -> producto,
                new EvaluadorCaida(() -> 0.0));
        return new Escenario(
                partida,
                control,
                atacante,
                objetivo,
                inventario,
                new EjecutorAtaqueConBotin(procesador));
    }

    private record Escenario(
            Partida partida,
            ControlAccionesTurno control,
            ParticipanteBotin atacante,
            ParticipanteBotin objetivo,
            InventarioPrueba inventario,
            EjecutorAtaqueConBotin ejecutor) {
    }

    private static final class InventarioPrueba implements InventarioBotin {

        private int consultas;
        private int otorgados;

        @Override
        public List<ElementoCandidatoBotin> listarCandidatos(
                String propietarioEnemigoId,
                String heroeEnemigoId) {
            consultas++;
            return List.of(new ElementoCandidatoBotin(
                    "elemento-item",
                    "producto-item",
                    TipoBotin.ITEM,
                    "Pocion enemiga",
                    null,
                    OrigenBotin.EQUIPADO));
        }

        @Override
        public void otorgar(String jugadorId, ElementoCandidatoBotin elemento) {
            otorgados++;
        }
    }
}
