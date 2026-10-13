package nexus.misiones.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.EventosDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El documento de un turno guarda el evento tal cual y lo devuelve igual; su id es determinista. */
class EventoDeCombateDocumentoTest {

    private static final UUID EJECUCION = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");

    @Test
    @DisplayName("ida y vuelta: el evento vuelve identico, con jugada y con sus campos opcionales")
    void idaYVuelta() {
        EventoDeCombate evento = EventosDePrueba.evento(EJECUCION, 7);

        EventoDeCombateDocumento documento = EventoDeCombateDocumento.de(evento, Instant.parse("2026-10-01T10:00:00Z"));

        assertThat(documento.aDominio()).isEqualTo(evento);
    }

    @Test
    @DisplayName("un turno sin jugada (el actor cayo al empezar) tambien vuelve identico")
    void sinJugada() {
        EventoDeCombate evento = EventosDePrueba.eventoSinJugada(EJECUCION, 2);

        assertThat(EventoDeCombateDocumento.de(evento, Instant.now()).aDominio()).isEqualTo(evento);
    }

    @Test
    @DisplayName("el id es ejecucion:secuencia, asi escribir dos veces el mismo turno no lo duplica")
    void idDeterminista() {
        EventoDeCombateDocumento documento = EventoDeCombateDocumento.de(EventosDePrueba.evento(EJECUCION, 7),
                Instant.now());

        assertThat(documento.id()).isEqualTo(EJECUCION + ":7");
        assertThat(documento.ejecucionId()).isEqualTo(EJECUCION.toString());
        assertThat(documento.secuencia()).isEqualTo(7);
    }
}
