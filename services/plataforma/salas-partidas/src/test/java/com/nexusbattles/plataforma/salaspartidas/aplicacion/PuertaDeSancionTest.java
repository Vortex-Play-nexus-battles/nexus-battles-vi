package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.JugadorSancionado;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.sanciones.SancionesNoDisponibles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * La puerta de sancion — HU-USR-005 y HU-USR-006, R10.2.
 *
 * <h2>El defecto que cierra</h2>
 *
 * Una suspension o un baneo emitidos por RF-USR-005/006 <b>no impedian
 * jugar</b>. El chat de la sala silenciaba al sancionado, y ahi acababa todo:
 * podia crear salas, entrar a las de otros y combatir con normalidad. La
 * sancion era, en la practica, un mute.
 *
 * <p>Se prueba a traves de los casos de uso reales y no de la clase auxiliar,
 * por lo mismo que {@link PuertaDeHeroeTest}: lo que importa no es que exista
 * un metodo, sino que <b>las dos puertas</b> lo usen y que el rechazo no deje
 * efectos a medias.
 */
@DisplayName("Puerta de sancion · un sancionado no crea salas ni entra (HU-USR-005/006)")
class PuertaDeSancionTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VISITANTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final int APUESTA = 150;

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final CanalDeSalaEspia canal = new CanalDeSalaEspia();
    private final InventarioEnMemoria inventario = InventarioEnMemoria.conHeroe();

    private static JugadorAutenticado como(UUID id) {
        return new JugadorAutenticado(id, "jugador-" + id.toString().charAt(0));
    }

    private static ParametrosDeSala parametros(int apuesta) {
        return new ParametrosDeSala(4, Modalidad.HASTA_SEIS, apuesta, false, false, null);
    }

    private CrearSala crearCon(SancionesEnMemoria sanciones, CreditosEnMemoria creditos) {
        return new CrearSala(salas, creditos, inventario, sanciones);
    }

    private IngresarASala entrarCon(SancionesEnMemoria sanciones, CreditosEnMemoria creditos) {
        return new IngresarASala(salas, canal, inventario, creditos, sanciones);
    }

    /** Una sala abierta por alguien SIN sancion, a la que otro intentara entrar. */
    private Sala salaAbierta(int apuesta) {
        CreditosEnMemoria creditos = new CreditosEnMemoria().conSaldo(ANFITRION, 1000);
        return crearCon(new SancionesEnMemoria(), creditos).ejecutar(parametros(apuesta), como(ANFITRION));
    }

    @Nested
    @DisplayName("con sancion activa")
    class Sancionado {

        @Test
        @DisplayName("no puede crear una sala")
        void noCrea() {
            SancionesEnMemoria sanciones = new SancionesEnMemoria().sancionado(ANFITRION);

            assertThrows(JugadorSancionado.class,
                    () -> crearCon(sanciones, new CreditosEnMemoria().conSaldo(ANFITRION, 1000))
                            .ejecutar(parametros(0), como(ANFITRION)));

            assertEquals(0L, salas.listar(null, null, 0, 10).totalElementos(), "no se guardo ninguna sala");
        }

        @Test
        @DisplayName("no puede entrar a la sala de otro")
        void noEntra() {
            Sala sala = salaAbierta(0);
            SancionesEnMemoria sanciones = new SancionesEnMemoria().sancionado(VISITANTE);

            assertThrows(JugadorSancionado.class,
                    () -> entrarCon(sanciones, new CreditosEnMemoria().conSaldo(VISITANTE, 1000))
                            .ejecutar(sala.id(), como(VISITANTE)));

            assertEquals(1, salas.buscarPorId(sala.id()).orElseThrow().participantes().size(),
                    "la sala sigue solo con su anfitrion");
        }

        @Test
        @DisplayName("el rechazo es 403 y no 422: no le falta nada, es que no puede")
        void esProhibido() {
            JugadorSancionado error = new JugadorSancionado();
            assertEquals(403, error.estado());
            assertEquals("https://nexusbattles.local/errores/jugador-sancionado",
                    error.tipo().toString());
        }

        @Test
        @DisplayName("no se le reserva ni un credito: la puerta va antes que el libro")
        void noSeReservaNada() {
            // Es la parte que un `if` puesto en el sitio equivocado rompe sin
            // que ninguna otra prueba se entere: la peticion falla igual, pero
            // deja una reserva viva a nombre de alguien que no va a jugar.
            SancionesEnMemoria sanciones = new SancionesEnMemoria().sancionado(VISITANTE);
            CreditosEnMemoria creditos = new CreditosEnMemoria().conSaldo(VISITANTE, 1000);
            Sala sala = salaAbierta(APUESTA);

            assertThrows(JugadorSancionado.class,
                    () -> entrarCon(sanciones, creditos).ejecutar(sala.id(), como(VISITANTE)));

            assertEquals(0, creditos.reservadoDe(VISITANTE), "no debio reservarse nada");
            assertEquals(1000, creditos.disponibleDe(VISITANTE), "su saldo queda intacto");
        }
    }

    @Nested
    @DisplayName("sin sancion, o sin poder comprobarla")
    class RestoDeCasos {

        @Test
        @DisplayName("quien no esta sancionado crea y entra con normalidad")
        void sinSancionTodoIgual() {
            Sala sala = salaAbierta(0);
            Sala conDos = entrarCon(new SancionesEnMemoria(), new CreditosEnMemoria())
                    .ejecutar(sala.id(), como(VISITANTE));

            assertEquals(2, conDos.participantes().size());
        }

        @Test
        @DisplayName("si sanciones no responde, NO se entra: fail-closed (D-14)")
        void sinRespuestaNoSeEntra() {
            // La alternativa seria dar por buena la entrada, y eso convierte
            // una caida en una via de escape: justo cuando el sistema esta
            // peor es cuando un sancionado podria colarse.
            Sala sala = salaAbierta(0);

            SancionesNoDisponibles fallo = assertThrows(SancionesNoDisponibles.class,
                    () -> entrarCon(new SancionesEnMemoria().caido(), new CreditosEnMemoria())
                            .ejecutar(sala.id(), como(VISITANTE)));

            assertEquals(503, fallo.estado(), "503, no 403: no es que no pueda, es que no se sabe");
            assertEquals(1, salas.buscarPorId(sala.id()).orElseThrow().participantes().size());
        }

        @Test
        @DisplayName("la sancion de uno no afecta a los demas")
        void soloAlSancionado() {
            Sala sala = salaAbierta(0);
            SancionesEnMemoria soloVisitante = new SancionesEnMemoria().sancionado(VISITANTE);

            UUID tercero = UUID.fromString("33333333-3333-3333-3333-333333333333");
            Sala conTercero = entrarCon(soloVisitante, new CreditosEnMemoria())
                    .ejecutar(sala.id(), como(tercero));

            assertEquals(2, conTercero.participantes().size());
        }
    }
}
