package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta.Accion;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta.Afectado;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Adaptador STOMP del canal de partida — HU-SAL-005.
 *
 * <p>Lo que se prueba es la traduccion: destino del canal {@code partidaEstado}
 * y mensaje con exactamente los campos que declara {@code AccionResuelta} en
 * {@code contracts/websocket/salas-partidas.yaml}. Se comprueba tambien lo que
 * NO lleva: ningun color ni porcentaje, porque el umbral es del cliente.
 */
@DisplayName("CanalDePartidaStomp · adaptador del canal de partida (HU-SAL-005)")
class CanalDePartidaStompTest {

    private static final UUID PARTIDA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ANA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRUNO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MAQUINA = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final SimpMessagingTemplate mensajeria = mock(SimpMessagingTemplate.class);
    private final CanalDePartidaStomp canal = new CanalDePartidaStomp(mensajeria);

    private static AccionResuelta flechaDoble() {
        return new AccionResuelta(PARTIDA, ANA,
                new Accion("FLECHA_DOBLE", "Flecha doble", null),
                List.of(new Afectado(BRUNO, 55, 100, -25),
                        new Afectado(MAQUINA, 30, 100, -70)));
    }

    private record Publicado(String destino, AvisoDeAccionResuelta aviso) {
    }

    private Publicado capturar() {
        ArgumentCaptor<String> destino = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        verify(mensajeria).convertAndSend(destino.capture(), cuerpo.capture());
        return new Publicado(destino.getValue(), (AvisoDeAccionResuelta) cuerpo.getValue());
    }

    @Test
    @DisplayName("publica en /tema/partidas/{idPartida}, el canal partidaEstado del contrato")
    void publicaEnElDestinoDelContrato() {
        canal.anunciarAccionResuelta(flechaDoble());

        assertEquals("/tema/partidas/" + PARTIDA, capturar().destino());
    }

    @Test
    @DisplayName("el mensaje lleva los cinco campos obligatorios del contrato")
    void elMensajeCumpleElContrato() {
        canal.anunciarAccionResuelta(flechaDoble());

        AvisoDeAccionResuelta aviso = capturar().aviso();
        assertAll(
                () -> assertEquals("partida.accion.resuelta", aviso.tipo()),
                () -> assertEquals(PARTIDA, aviso.idPartida()),
                () -> assertEquals(ANA, aviso.idEjecutor()),
                () -> assertEquals("FLECHA_DOBLE", aviso.accion().codigo()),
                () -> assertEquals("Flecha doble", aviso.accion().nombre()),
                () -> assertNull(aviso.accion().icono()),
                () -> assertEquals(2, aviso.afectados().size()));
    }

    @Test
    @DisplayName("todos los afectados viajan en un solo mensaje, con vida actual y maxima")
    void todosLosAfectadosEnUnMensaje() {
        // Criterio 3: «para todos los participantes». Un mensaje por afectado
        // dejaria las barras desincronizadas durante un instante.
        canal.anunciarAccionResuelta(flechaDoble());

        List<AvisoDeAccionResuelta.Afectado> afectados = capturar().aviso().afectados();
        assertAll(
                () -> assertEquals(BRUNO, afectados.get(0).idJugador()),
                () -> assertEquals(55, afectados.get(0).vidaActual()),
                () -> assertEquals(100, afectados.get(0).vidaMaxima()),
                () -> assertEquals(-25, afectados.get(0).diferencia()),
                () -> assertEquals(MAQUINA, afectados.get(1).idJugador()),
                () -> assertEquals(30, afectados.get(1).vidaActual()),
                () -> assertEquals(-70, afectados.get(1).diferencia()));
    }

    @Test
    @DisplayName("el mensaje no lleva color ni porcentaje: el umbral es del cliente (RF-JUE-009)")
    void sinColorNiPorcentaje() {
        canal.anunciarAccionResuelta(flechaDoble());

        String forma = capturar().aviso().toString();
        assertTrue(!forma.contains("color") && !forma.contains("porcentaje")
                && !forma.contains("estado"), forma);
    }

    @Test
    @DisplayName("cada partida publica en su propio destino")
    void cadaPartidaTieneSuDestino() {
        UUID otra = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertEquals("/tema/partidas/" + otra, CanalDePartidaStomp.destinoDe(otra));
    }

    // =========================================================================
    // Arranque y turnos — HU-SAL-004, RF-JUE-017
    // =========================================================================

    /** Sala con dos humanos, cada uno con SU heroe: la vida no puede salir igual. */
    private static Sala salaDeEjemplo() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, false, false, null), ANA,
                new FichaDeParticipante("Ana",
                        new HeroeDeCombate("h-ana", "Arquero del Norte", null, 5, 120, 120)));
        sala.unirse(BRUNO,
                new FichaDeParticipante("Bruno",
                        new HeroeDeCombate("h-bruno", "Centinela", null, 3, 90, 90)),
                null);
        return sala;
    }

    private static Partida partidaDe(Sala sala) {
        return Partida.iniciar(sala, Instant.parse("2026-09-17T20:00:00Z"));
    }

    /** Los dos destinos y los dos cuerpos de una publicacion doble, en orden. */
    private record Doble(List<String> destinos, List<Object> cuerpos) {
    }

    private Doble capturarDos() {
        ArgumentCaptor<String> destino = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        verify(mensajeria, times(2)).convertAndSend(destino.capture(), cuerpo.capture());
        return new Doble(destino.getAllValues(), cuerpo.getAllValues());
    }

    @Test
    @DisplayName("el inicio se anuncia tambien en el canal de la SALA, no solo en el de la partida")
    void elInicioViajaPorLosDosCanales() {
        // Quien espera en la sala todavia no conoce el id de la partida, asi que
        // no puede estar suscrito a su tema. Si el aviso solo fuera por ahi, el
        // anfitrion entraria al combate y los demas se quedarian esperando.
        Sala sala = salaDeEjemplo();
        Partida partida = partidaDe(sala);

        canal.anunciarInicio(sala, partida);

        assertEquals(
                List.of("/tema/partidas/" + partida.id(), "/tema/salas/" + partida.idSala()),
                capturarDos().destinos());
    }

    @Test
    @DisplayName("el aviso de inicio lleva los cinco campos obligatorios del contrato")
    void elAvisoDeInicioCumpleElContrato() {
        // El tipo y los nombres salen de `PartidaIniciada` en
        // contracts/websocket/salas-partidas.yaml, no al reves.
        Sala sala = salaDeEjemplo();
        Partida partida = partidaDe(sala);

        canal.anunciarInicio(sala, partida);

        AvisoDeInicioDePartida aviso = (AvisoDeInicioDePartida) capturarDos().cuerpos().get(0);
        assertAll(
                () -> assertEquals("sala.partida.iniciada", aviso.tipo()),
                () -> assertEquals(partida.id(), aviso.idPartida()),
                () -> assertEquals(partida.idSala(), aviso.idSala()),
                () -> assertEquals(ANA, aviso.turnoActual().idJugador()),
                () -> assertEquals(1, aviso.turnoActual().numeroTurno()));
    }

    @Test
    @DisplayName("el orden de turnos viaja completo: es una salida que exige el PDF")
    void elOrdenDeTurnosViajaCompleto() {
        Sala sala = salaDeEjemplo();
        Partida partida = partidaDe(sala);

        canal.anunciarInicio(sala, partida);

        AvisoDeInicioDePartida aviso = (AvisoDeInicioDePartida) capturarDos().cuerpos().get(0);
        assertAll(
                () -> assertEquals(List.of(ANA, BRUNO), aviso.ordenDeTurnos()),
                () -> assertEquals(aviso.ordenDeTurnos().get(0), aviso.turnoActual().idJugador(),
                        "quien abre es siempre el primero del orden"));
    }

    @Test
    @DisplayName("los dos canales reciben el mismo aviso, no dos versiones distintas")
    void losDosCanalesRecibenLoMismo() {
        Sala sala = salaDeEjemplo();
        canal.anunciarInicio(sala, partidaDe(sala));

        List<Object> cuerpos = capturarDos().cuerpos();
        assertEquals(cuerpos.get(0), cuerpos.get(1));
    }

    @Test
    @DisplayName("el aviso lleva el roster con el heroe y la vida inicial de cada uno")
    void elRosterLlevaLosHeroesReales() {
        // Es lo que P2.4 vino a arreglar: antes el aviso no traia participantes
        // y la barra de vida no tenia de donde salir.
        Sala sala = salaDeEjemplo();

        canal.anunciarInicio(sala, partidaDe(sala));

        List<AvisoDeInicioDePartida.Participante> roster =
                ((AvisoDeInicioDePartida) capturarDos().cuerpos().get(0)).participantes();
        assertAll(
                () -> assertEquals(2, roster.size()),
                () -> assertEquals(ANA, roster.get(0).jugador().id()),
                () -> assertEquals("Ana", roster.get(0).jugador().apodo()),
                () -> assertEquals("Arquero del Norte", roster.get(0).heroe().nombre()),
                () -> assertEquals(120, roster.get(0).heroe().vidaActual()),
                () -> assertEquals(120, roster.get(0).heroe().vidaMaxima()),
                // Heroes DISTINTOS: si el segundo saliera con la vida del
                // primero, la barra estaria copiando en vez de leyendo.
                () -> assertEquals("Bruno", roster.get(1).jugador().apodo()),
                () -> assertEquals("Centinela", roster.get(1).heroe().nombre()),
                () -> assertEquals(90, roster.get(1).heroe().vidaMaxima()),
                () -> assertEquals(false, roster.get(1).esIA()));
    }

    @Test
    @DisplayName("la IA viaja con el heroe del anfitrion, no con uno inventado (HU-SAL-004)")
    void laIaViajaConElHeroeDelAnfitrion() {
        Sala conIa = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANA,
                new FichaDeParticipante("Ana",
                        new HeroeDeCombate("h-ana", "Arquero del Norte", null, 5, 120, 120)));

        canal.anunciarInicio(conIa, partidaDe(conIa));

        List<AvisoDeInicioDePartida.Participante> roster =
                ((AvisoDeInicioDePartida) capturarDos().cuerpos().get(0)).participantes();
        assertAll(
                () -> assertEquals(2, roster.size()),
                () -> assertTrue(roster.get(1).esIA()),
                () -> assertEquals("Arquero del Norte", roster.get(1).heroe().nombre(),
                        "la IA usa el heroe del anfitrion: el unico que la partida conoce"),
                () -> assertEquals(120, roster.get(1).heroe().vidaActual()),
                () -> assertEquals("Heroe de la IA", roster.get(1).jugador().apodo()));
    }

    @Test
    @DisplayName("el cambio de turno se publica solo en el tema de la partida")
    void elTurnoSoloEnLaPartida() {
        Partida partida = partidaDe(salaDeEjemplo());
        partida.avanzarTurno();

        canal.anunciarTurno(partida);

        ArgumentCaptor<String> destino = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        verify(mensajeria).convertAndSend(destino.capture(), cuerpo.capture());

        AvisoDeTurno aviso = (AvisoDeTurno) cuerpo.getValue();
        assertAll(
                () -> assertEquals("/tema/partidas/" + partida.id(), destino.getValue()),
                () -> assertEquals("partida.turno.cambiado", aviso.tipo()),
                () -> assertEquals(partida.id(), aviso.idPartida()),
                () -> assertEquals(BRUNO, aviso.idJugador()),
                () -> assertEquals(2, aviso.numeroTurno()));
    }

    /* HU-JUE-014, CA-04: el fin lleva el reparto de la apuesta, con la forma del AsyncAPI. */

    @Test
    @DisplayName("partida.finalizada lleva ganadores y el reparto de la apuesta por jugador")
    void elFinLlevaElReparto() throws Exception {
        Partida partida = partidaDe(salaDeEjemplo());
        partida.aplicarDano(BRUNO, 1_000);
        partida.terminarSiSoloQuedaUno();

        canal.anunciarFin(partida, List.of(
                new com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos(ANA, 100),
                new com.nexusbattles.plataforma.salaspartidas.dominio.RepartoDeCreditos(BRUNO, -100)));

        ArgumentCaptor<String> destino = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        verify(mensajeria).convertAndSend(destino.capture(), cuerpo.capture());
        AvisoDePartidaFinalizada aviso = (AvisoDePartidaFinalizada) cuerpo.getValue();
        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(aviso);

        assertAll(
                () -> assertEquals("/tema/partidas/" + partida.id(), destino.getValue()),
                () -> assertEquals("partida.finalizada", aviso.tipo()),
                () -> assertEquals(List.of(ANA), aviso.ganadores()),
                () -> assertEquals(2, aviso.reparto().size()),
                () -> assertEquals(ANA, aviso.reparto().get(0).idJugador()),
                () -> assertEquals(100, aviso.reparto().get(0).creditos()),
                () -> assertEquals(-100, aviso.reparto().get(1).creditos()),
                () -> assertTrue(json.contains("\"reparto\":[{\"idJugador\":\"" + ANA + "\",\"creditos\":100}"),
                        "los nombres de campo son los del contrato: " + json));
    }

    @Test
    @DisplayName("sin apuesta (o con la liquidacion pendiente) el fin viaja sin la clave reparto")
    void sinRepartoNoViajaLaClave() throws Exception {
        Partida partida = partidaDe(salaDeEjemplo());
        partida.aplicarDano(BRUNO, 1_000);
        partida.terminarSiSoloQuedaUno();

        canal.anunciarFin(partida, List.of());

        ArgumentCaptor<Object> cuerpo = ArgumentCaptor.forClass(Object.class);
        verify(mensajeria).convertAndSend(org.mockito.ArgumentMatchers.anyString(), cuerpo.capture());
        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(cuerpo.getValue());

        assertAll(
                () -> assertTrue(!json.contains("reparto"), "reparto es opcional en el contrato y aqui se omite: " + json),
                () -> assertTrue(json.contains("\"ganadores\":[\"" + ANA + "\"]")));
    }
}
