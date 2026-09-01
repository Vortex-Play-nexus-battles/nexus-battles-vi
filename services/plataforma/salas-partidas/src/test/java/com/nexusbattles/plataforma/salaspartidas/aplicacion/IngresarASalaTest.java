package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
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
 * Caso de uso de ingreso a una sala — HU-SAL-002, RF-JUE-002.
 *
 * <p>Aqui no se repiten las reglas del dominio: eso es {@code SalaTest}. Lo que
 * se prueba es la coordinacion — que se busque la sala, que un identificador
 * desconocido se distinga de un rechazo por reglas, y que el ingreso quede
 * guardado.
 *
 * <p>Los codigos de estado salen del contrato OpenAPI, que distingue tres
 * rechazos distintos: 404 si la sala no existe, 409 si esta llena o la partida
 * ya empezo, y 403 si es privada y falta la invitacion.
 *
 * <p>Un participante es un UUID y nada mas. El apodo no viaja: pertenece al
 * modulo de cuentas y ninguna pantalla de HU-SAL-002 lo muestra.
 *
 * <p>NO se prueba el heroe equipado (HU-SAL-003) ni los creditos de la apuesta,
 * que dependen de modulos de otros equipos.
 */
@DisplayName("IngresarASala · caso de uso (HU-SAL-002)")
class IngresarASalaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VISITANTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private RepositorioDeSalasEnMemoria repositorio;
    private IngresarASala ingresarASala;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioDeSalasEnMemoria();
        ingresarASala = new IngresarASala(repositorio);
    }

    private Sala salaAbierta() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION);
        return repositorio.guardar(sala);
    }

    @Test
    @DisplayName("admite al jugador y devuelve la sala con el dentro")
    void ingresa() {
        Sala sala = salaAbierta();

        Sala resultado = ingresarASala.ejecutar(sala.id(), VISITANTE);

        assertAll(
                () -> assertEquals(2, resultado.ocupacion()),
                () -> assertTrue(resultado.participantes().contains(VISITANTE)));
    }

    @Test
    @DisplayName("el ingreso queda guardado, no solo devuelto")
    void elIngresoPersiste() {
        Sala sala = salaAbierta();

        ingresarASala.ejecutar(sala.id(), VISITANTE);

        Sala guardada = repositorio.buscarPorId(sala.id()).orElseThrow();
        assertTrue(guardada.participantes().contains(VISITANTE));
    }

    @Test
    @DisplayName("una sala que no existe se distingue de un rechazo por reglas: 404")
    void salaInexistente() {
        SalaNoEncontrada error = assertThrows(SalaNoEncontrada.class,
                () -> ingresarASala.ejecutar(UUID.randomUUID(), VISITANTE));

        assertEquals(404, error.estado());
    }

    @Test
    @DisplayName("una sala llena rechaza con 409, como fija el contrato")
    void salaLlena() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION);
        sala.unirse(VISITANTE);
        repositorio.guardar(sala);

        IngresoNoPermitido error = assertThrows(IngresoNoPermitido.class,
                () -> ingresarASala.ejecutar(sala.id(), UUID.randomUUID()));

        assertEquals(409, error.estado());
    }

    @Test
    @DisplayName("una sala privada rechaza con 403, no con 409")
    void salaPrivada() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, true, null), ANFITRION);
        repositorio.guardar(sala);

        SalaPrivadaSinInvitacion error = assertThrows(SalaPrivadaSinInvitacion.class,
                () -> ingresarASala.ejecutar(sala.id(), VISITANTE));

        assertEquals(403, error.estado());
    }

    @Test
    @DisplayName("quien ya esta dentro no vuelve a entrar")
    void ingresoRepetido() {
        Sala sala = salaAbierta();
        ingresarASala.ejecutar(sala.id(), VISITANTE);

        assertThrows(IngresoNoPermitido.class,
                () -> ingresarASala.ejecutar(sala.id(), VISITANTE));
    }

    @Test
    @DisplayName("si el ingreso se rechaza, la sala guardada no cambia")
    void unRechazoNoDejaRastro() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION);
        sala.unirse(VISITANTE);
        repositorio.guardar(sala);

        assertThrows(IngresoNoPermitido.class,
                () -> ingresarASala.ejecutar(sala.id(), UUID.randomUUID()));

        Sala guardada = repositorio.buscarPorId(sala.id()).orElseThrow();
        assertAll(
                () -> assertEquals(2, guardada.ocupacion()),
                () -> assertEquals(EstadoSala.LLENA, guardada.estado()));
    }

    @Test
    @DisplayName("exige sala y jugador identificados")
    void exigeIdentificadores() {
        Sala sala = salaAbierta();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> ingresarASala.ejecutar(null, VISITANTE)),
                () -> assertThrows(NullPointerException.class,
                        () -> ingresarASala.ejecutar(sala.id(), null)));
    }
}
