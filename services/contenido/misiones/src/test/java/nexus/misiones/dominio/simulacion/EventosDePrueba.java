package nexus.misiones.dominio.simulacion;

import java.util.List;
import java.util.UUID;

/** Eventos de combate completos para las pruebas de persistencia: con todos sus campos llenos. */
public final class EventosDePrueba {

    private EventosDePrueba() {
    }

    public static EventoDeCombate evento(UUID ejecucion, int secuencia) {
        EventoDeCombate.EstadoDeCombatiente heroe = new EventoDeCombate.EstadoDeCombatiente(44, 60, 8, 12,
                List.of(new EventoDeCombate.Recarga("Embate sangriento", 1)),
                List.of(new EventoDeCombate.EfectoVigente("Sangrado", "DANO_POR_TURNO", 2, 1)));
        EventoDeCombate.EstadoDeCombatiente enemigo = new EventoDeCombate.EstadoDeCombatiente(30, 40, 10, 10,
                List.of(), List.of());
        EventoDeCombate.Estados antes = new EventoDeCombate.Estados(heroe, enemigo);
        EventoDeCombate.Estados despues = new EventoDeCombate.Estados(
                new EventoDeCombate.EstadoDeCombatiente(44, 60, 4, 12, List.of(), List.of()),
                new EventoDeCombate.EstadoDeCombatiente(18, 40, 10, 10, List.of(), List.of()));
        EventoDeCombate.Resultado resultado = new EventoDeCombate.Resultado("CAUSAR_DANO_CRITICO", true, 17, 11, 150,
                8, 12, true, List.of(new Suceso("DANO", "ENEMIGO", "HEROE", "Embate sangriento", 12)));
        EventoDeCombate.Jugada jugada = new EventoDeCombate.Jugada("Embate sangriento", "Embate sangriento", false, 4,
                4, List.of(new EventoDeCombate.Rechazo("Golpe con escudo", "EN_CARGA")), resultado);
        return new EventoDeCombate(ejecucion, "templo-olvidado", secuencia, 3, "Guardianes de Piedra", 2,
                new EventoDeCombate.Actor(EventoDeCombate.Lado.HEROE, "Vorn", "Guerrero Armas", 5), antes,
                List.of(new Suceso("PODER_RECUPERADO", "HEROE", null, null, 2)), jugada, despues);
    }

    /** Un turno en que el actor cayo al empezar: sin jugada, con campos nulos. */
    public static EventoDeCombate eventoSinJugada(UUID ejecucion, int secuencia) {
        EventoDeCombate completo = evento(ejecucion, secuencia);
        return new EventoDeCombate(ejecucion, completo.misionId(), secuencia, completo.encuentro(),
                completo.enemigo(), completo.turno(), completo.actor(), completo.antes(),
                List.of(new Suceso("DANO_POR_TURNO", "HEROE", "ENEMIGO", "Sangrado", 44)), null, completo.despues());
    }
}
