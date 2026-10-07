package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InvitacionNoPermitida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.DirectorioDeJugadores;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invitar a un jugador a la sala — salas-partidas.yaml 1.10.0 (revision del modo
 * jugador del 6-oct, punto 13: «invitar amigos o buscar por nombre»).
 */
@DisplayName("InvitarASala · el anfitrion invita por apodo (1.10.0)")
class InvitarASalaTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant AHORA = Instant.parse("2026-10-06T18:00:00Z");
    private static final JugadorAutenticado ANFITRIONA = new JugadorAutenticado(ANA, "Ana_Nexo");

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final DirectorioEnMemoria directorio = new DirectorioEnMemoria()
            .con(BRUNO, "Perez_Bro15", DirectorioDeJugadores.CuentaDeJugador.ACTIVO)
            .con(CARLA, "Carla", "SUSPENDIDO");
    private final AvisosEspia avisos = new AvisosEspia();

    private InvitarASala casoDeUso() {
        return new InvitarASala(salas, directorio, avisos, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static HeroeDeCombate heroe() {
        return HeroeDeCombate.aPleno("h-1", "Sombra de Vael", 140);
    }

    private Sala sala(int maximo, Modalidad modalidad, int recompensa, boolean privada) {
        return salas.guardar(Sala.crear(new ParametrosDeSala(maximo, modalidad, recompensa, false, privada, null),
                ANA, new FichaDeParticipante("Ana_Nexo", heroe())));
    }

    @Test
    @DisplayName("invita a un jugador activo: el aviso lleva quien invita, la sala y la apuesta")
    void invita() {
        Sala sala = sala(2, Modalidad.UNO_CONTRA_UNO, 50, false);

        InvitarASala.Resultado resultado = casoDeUso().ejecutar(sala.id(), ANFITRIONA, BRUNO);

        AvisoDeInvitacion.Invitacion enviada = avisos.enviadas.get(0);
        assertAll(
                () -> assertEquals(BRUNO, resultado.idJugador()),
                () -> assertEquals("Perez_Bro15", resultado.apodo(), "el anfitrion ve el apodo, nunca el uid"),
                () -> assertTrue(resultado.enviada()),
                () -> assertEquals(sala.id(), enviada.idSala()),
                () -> assertEquals(BRUNO, enviada.idInvitado()),
                () -> assertEquals("Ana_Nexo", enviada.apodoAnfitrion()),
                () -> assertEquals("UNO_CONTRA_UNO", enviada.modalidad()),
                () -> assertEquals(50, enviada.recompensa()),
                () -> assertFalse(enviada.privada()),
                () -> assertNull(enviada.codigo(), "una sala publica no tiene codigo que mandar"),
                () -> assertEquals(AHORA, enviada.enviadaEn()));
    }

    @Test
    @DisplayName("en una sala privada el aviso lleva el codigo: es lo que le deja entrar")
    void privadaLlevaElCodigo() {
        Sala sala = sala(4, Modalidad.HASTA_SEIS, 0, true);

        casoDeUso().ejecutar(sala.id(), ANFITRIONA, BRUNO);

        assertAll(
                () -> assertTrue(avisos.enviadas.get(0).privada()),
                () -> assertEquals(sala.codigoInvitacion(), avisos.enviadas.get(0).codigo()));
    }

    @Test
    @DisplayName("invitar otra vez a la misma persona no es un error: dice que ya la tenia")
    void otraVez() {
        Sala sala = sala(2, Modalidad.UNO_CONTRA_UNO, 0, false);
        avisos.yaEstaba = true;

        InvitarASala.Resultado resultado = casoDeUso().ejecutar(sala.id(), ANFITRIONA, BRUNO);

        assertFalse(resultado.enviada());
    }

    @Test
    @DisplayName("solo invita el anfitrion: 403 y no sale ningun aviso")
    void soloElAnfitrion() {
        Sala sala = sala(4, Modalidad.HASTA_SEIS, 0, true);
        sala.unirse(BRUNO, new FichaDeParticipante("Perez_Bro15", heroe()), sala.codigoInvitacion());
        salas.guardar(sala);

        InvitacionNoPermitida rechazo = assertThrows(InvitacionNoPermitida.class,
                () -> casoDeUso().ejecutar(sala.id(), new JugadorAutenticado(BRUNO, "Perez_Bro15"), CARLA));

        assertAll(
                () -> assertEquals(403, rechazo.estado()),
                () -> assertTrue(avisos.enviadas.isEmpty()));
    }

    @Test
    @DisplayName("no te puedes invitar a ti mismo")
    void aSiMismo() {
        Sala sala = sala(2, Modalidad.UNO_CONTRA_UNO, 0, false);

        InvitacionNoPermitida rechazo = assertThrows(InvitacionNoPermitida.class,
                () -> casoDeUso().ejecutar(sala.id(), ANFITRIONA, ANA));

        assertEquals(422, rechazo.estado());
    }

    @Test
    @DisplayName("a quien ya esta dentro no se le invita")
    void yaDentro() {
        Sala sala = sala(4, Modalidad.HASTA_SEIS, 0, false);
        sala.unirse(BRUNO, new FichaDeParticipante("Perez_Bro15", heroe()), null);
        salas.guardar(sala);

        InvitacionNoPermitida rechazo = assertThrows(InvitacionNoPermitida.class,
                () -> casoDeUso().ejecutar(sala.id(), ANFITRIONA, BRUNO));

        assertEquals(409, rechazo.estado());
    }

    @Test
    @DisplayName("una sala completa no admite invitaciones")
    void llena() {
        Sala sala = sala(2, Modalidad.UNO_CONTRA_UNO, 0, false);
        sala.unirse(CARLA, new FichaDeParticipante("Carla", heroe()), null);
        salas.guardar(sala);
        assertEquals(EstadoSala.LLENA, sala.estado(), "precondicion");

        InvitacionNoPermitida rechazo = assertThrows(InvitacionNoPermitida.class,
                () -> casoDeUso().ejecutar(sala.id(), ANFITRIONA, BRUNO));

        assertAll(
                () -> assertEquals(409, rechazo.estado()),
                () -> assertEquals("La sala está completa.", rechazo.detalle()));
    }

    @Test
    @DisplayName("una sala contra la IA nace completa: no hay a quien invitar")
    void contraLaIa() {
        Sala sala = salas.guardar(Sala.crear(new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null),
                ANA, new FichaDeParticipante("Ana_Nexo", heroe())));

        assertThrows(InvitacionNoPermitida.class, () -> casoDeUso().ejecutar(sala.id(), ANFITRIONA, BRUNO));
        assertTrue(avisos.enviadas.isEmpty());
    }

    @Test
    @DisplayName("una sala cancelada ya no espera a nadie")
    void cancelada() {
        Sala sala = sala(4, Modalidad.HASTA_SEIS, 0, false);
        sala.cancelar(ANA);
        salas.guardar(sala);

        InvitacionNoPermitida rechazo = assertThrows(InvitacionNoPermitida.class,
                () -> casoDeUso().ejecutar(sala.id(), ANFITRIONA, BRUNO));

        assertEquals("La sala ya no espera jugadores.", rechazo.detalle());
    }

    @Test
    @DisplayName("un jugador que no existe o con la cuenta suspendida: 404, sin decir cual de las dos")
    void noExisteONoActivo() {
        Sala sala = sala(4, Modalidad.HASTA_SEIS, 0, false);

        InvitacionNoPermitida inexistente = assertThrows(InvitacionNoPermitida.class,
                () -> casoDeUso().ejecutar(sala.id(), ANFITRIONA, UUID.randomUUID()));
        InvitacionNoPermitida suspendida = assertThrows(InvitacionNoPermitida.class,
                () -> casoDeUso().ejecutar(sala.id(), ANFITRIONA, CARLA));

        assertAll(
                () -> assertEquals(404, inexistente.estado()),
                () -> assertEquals(inexistente.detalle(), suspendida.detalle()),
                () -> assertTrue(avisos.enviadas.isEmpty()));
    }

    @Test
    @DisplayName("si ms-identidad no contesta, 503: no se manda una invitacion a ciegas")
    void sinDirectorio() {
        Sala sala = sala(4, Modalidad.HASTA_SEIS, 0, false);
        directorio.caido = true;

        InvitacionNoPermitida rechazo = assertThrows(InvitacionNoPermitida.class,
                () -> casoDeUso().ejecutar(sala.id(), ANFITRIONA, BRUNO));

        assertAll(
                () -> assertEquals(503, rechazo.estado()),
                () -> assertTrue(avisos.enviadas.isEmpty()));
    }

    @Test
    @DisplayName("si notificaciones no contesta, 503: la invitacion no salio y se dice")
    void sinNotificaciones() {
        Sala sala = sala(4, Modalidad.HASTA_SEIS, 0, false);
        avisos.caido = true;

        InvitacionNoPermitida rechazo = assertThrows(InvitacionNoPermitida.class,
                () -> casoDeUso().ejecutar(sala.id(), ANFITRIONA, BRUNO));

        assertEquals(503, rechazo.estado());
    }

    @Test
    @DisplayName("una sala que no existe es 404 de sala")
    void salaInexistente() {
        assertThrows(SalaNoEncontrada.class, () -> casoDeUso().ejecutar(UUID.randomUUID(), ANFITRIONA, BRUNO));
    }

    /** ms-identidad en memoria: uid → cuenta. */
    static final class DirectorioEnMemoria implements DirectorioDeJugadores {
        private final Map<UUID, CuentaDeJugador> cuentas = new HashMap<>();
        boolean caido;

        DirectorioEnMemoria con(UUID uid, String apodo, String estado) {
            cuentas.put(uid, new CuentaDeJugador(uid, apodo, estado));
            return this;
        }

        @Override
        public Optional<CuentaDeJugador> buscar(UUID uid) {
            if (caido) {
                throw new DirectorioNoDisponible("ms-identidad no contesta");
            }
            return Optional.ofNullable(cuentas.get(uid));
        }
    }

    /** Las invitaciones que salieron. */
    static final class AvisosEspia implements AvisoDeInvitacion {
        final List<Invitacion> enviadas = new ArrayList<>();
        boolean yaEstaba;
        boolean caido;

        @Override
        public boolean invitar(Invitacion invitacion) {
            if (caido) {
                throw new AvisoNoDisponible("notificaciones no contesta");
            }
            enviadas.add(invitacion);
            return !yaEstaba;
        }
    }
}
