package nexus.combate.arranque;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import nexus.combate.CatalogoBotin;
import nexus.combate.ElementoCandidatoBotin;
import nexus.combate.FabricaProcesadorPerdidaEquipo;
import nexus.combate.InventarioBotin;
import nexus.combate.MotivoFinPartida;
import nexus.combate.OrigenBotin;
import nexus.combate.ParticipantePerdidaEquipo;
import nexus.combate.ProductoBotin;
import nexus.combate.ResultadoPartida;
import nexus.combate.TipoBotin;
import nexus.combate.TransferenciaEquipo;
import nexus.combate.TransferidorEquipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BotinConfigurationTest {

    @Test
    @DisplayName("un reintento de la misma partida conserva el ganador asignado")
    void seleccionDeterministaPorPartida() {
        InventarioBotin inventario = (propietarioId, heroeId) -> "perdedor".equals(propietarioId)
                ? List.of(new ElementoCandidatoBotin(
                        "elemento-1", "producto-1", TipoBotin.ITEM, "Objeto", null, OrigenBotin.EQUIPADO))
                : List.of();
        CatalogoBotin catalogo = productoId -> new ProductoBotin(
                productoId, "Objeto", TipoBotin.ITEM, null, BigDecimal.valueOf(60));
        TransferidorEspia transferidor = new TransferidorEspia();
        FabricaProcesadorPerdidaEquipo fabrica = new BotinConfiguration()
                .fabricaProcesadorPerdidaEquipo(inventario, catalogo, transferidor);
        List<ParticipantePerdidaEquipo> participantes = List.of(
                new ParticipantePerdidaEquipo("g-1", "equipo-a", "ganador-1", "heroe-g-1"),
                new ParticipantePerdidaEquipo("g-2", "equipo-a", "ganador-2", "heroe-g-2"),
                new ParticipantePerdidaEquipo("p-1", "equipo-b", "perdedor", "heroe-p-1"));
        ResultadoPartida cierre = new ResultadoPartida("equipo-a", MotivoFinPartida.SUPERVIVENCIA);

        fabrica.crear("botin-partida:abc", participantes).procesar(cierre);
        fabrica.crear("botin-partida:abc", participantes).procesar(cierre);

        assertEquals(2, transferidor.destinos.size());
        assertEquals(transferidor.destinos.get(0), transferidor.destinos.get(1));
    }

    private static final class TransferidorEspia implements TransferidorEquipo {

        private final List<String> destinos = new ArrayList<>();

        @Override
        public void transferir(String operacionId, List<TransferenciaEquipo> transferencias) {
            destinos.add(transferencias.getFirst().propietarioDestinoId());
        }
    }
}
