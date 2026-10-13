package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
import com.nexusbattles.plataforma.salaspartidas.dominio.JugadorSancionado;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaPrivadaSinInvitacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La pregunta previa al ingreso — RFINAL-04, salas-partidas.yaml 1.9.0.
 *
 * <p>Lo que importa es lo que NO hace: no mete a nadie, no anuncia nada y no
 * deja rastro en la sala. Las reglas en si son las de {@code Sala} y se prueban
 * en {@code SalaTest}; aqui, que se pregunten en el orden del ingreso.
 */
@DisplayName("ComprobarIngreso · el codigo primero, sin efectos (RFINAL-04)")
class ComprobarIngresoTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VISITANTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private RepositorioDeSalasEnMemoria repositorio;
    private SancionesEnMemoria sanciones;
    private ComprobarIngreso comprobar;

    private static JugadorAutenticado como(UUID id) {
        return new JugadorAutenticado(id, "jugador-" + id.toString().substring(0, 8));
    }

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioDeSalasEnMemoria();
        sanciones = new SancionesEnMemoria();
        comprobar = new ComprobarIngreso(repositorio, sanciones);
    }

    private Sala privada(int maximo) {
        return repositorio.guardar(Sala.crear(
                new ParametrosDeSala(maximo, maximo == 2 ? Modalidad.UNO_CONTRA_UNO : Modalidad.HASTA_SEIS,
                        500, false, true, null),
                ANFITRION));
    }

    @Test
    @DisplayName("con el codigo bueno devuelve la sala tal cual: nadie entra")
    void codigoBueno() {
        Sala sala = privada(4);

        Sala vista = comprobar.ejecutar(sala.id(), como(VISITANTE), sala.codigoInvitacion());

        Sala guardada = repositorio.buscarPorId(sala.id()).orElseThrow();
        assertAll(
                () -> assertEquals(sala.id(), vista.id()),
                () -> assertEquals(1, guardada.ocupacion(), "comprobar no es entrar"),
                () -> assertTrue(!guardada.participantes().contains(VISITANTE)));
    }

    @Test
    @DisplayName("«ZZZZ-9999» en una privada: 403 antes de cualquier verificacion de heroe")
    void codigoEquivocado() {
        Sala sala = privada(4);

        SalaPrivadaSinInvitacion error = assertThrows(SalaPrivadaSinInvitacion.class,
                () -> comprobar.ejecutar(sala.id(), como(VISITANTE), "ZZZZ-9999"));

        assertEquals(403, error.estado());
    }

    @Test
    @DisplayName("sin codigo, una privada tambien responde 403")
    void sinCodigo() {
        Sala sala = privada(4);

        assertThrows(SalaPrivadaSinInvitacion.class,
                () -> comprobar.ejecutar(sala.id(), como(VISITANTE), null));
    }

    @Test
    @DisplayName("una publica no pide codigo")
    void publica() {
        Sala sala = repositorio.guardar(Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION));

        assertEquals(sala.id(), comprobar.ejecutar(sala.id(), como(VISITANTE), null).id());
    }

    @Test
    @DisplayName("una sala llena responde 409 aunque el codigo sea bueno")
    void llena() {
        Sala sala = privada(2);
        sala.unirse(UUID.randomUUID(), sala.codigoInvitacion());
        repositorio.guardar(sala);

        IngresoNoPermitido error = assertThrows(IngresoNoPermitido.class,
                () -> comprobar.ejecutar(sala.id(), como(VISITANTE), sala.codigoInvitacion()));

        assertEquals(409, error.estado());
    }

    @Test
    @DisplayName("una sala que no existe responde 404")
    void inexistente() {
        assertThrows(SalaNoEncontrada.class,
                () -> comprobar.ejecutar(UUID.randomUUID(), como(VISITANTE), "ABCD-2345"));
    }

    @Test
    @DisplayName("un jugador sancionado se entera aqui, igual que al entrar (403)")
    void sancionado() {
        Sala sala = privada(4);
        sanciones.sancionado(VISITANTE);

        assertThrows(JugadorSancionado.class,
                () -> comprobar.ejecutar(sala.id(), como(VISITANTE), sala.codigoInvitacion()));
    }
}
