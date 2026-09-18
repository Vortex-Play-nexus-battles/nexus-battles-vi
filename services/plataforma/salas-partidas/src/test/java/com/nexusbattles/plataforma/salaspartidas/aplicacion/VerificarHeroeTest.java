package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoVerificacion;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificacion previa de heroe — HU-SAL-003, RF-JUE-003.
 *
 * <p>Lo que se prueba es la composicion: que el veredicto del inventario llegue
 * intacto a la respuesta, que la recompensa salga de la sala y que la
 * verificacion no toque nada. Lo que decide si un heroe esta equipado se prueba
 * en el adaptador, porque esa regla es del proveedor.
 */
@DisplayName("VerificarHeroe · verificacion previa sin efectos (HU-SAL-003)")
class VerificarHeroeTest {

    private static final UUID JUGADOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String APODO = "vael";

    private final RepositorioDeSalasEnMemoria repositorio = new RepositorioDeSalasEnMemoria();

    private static JugadorAutenticado jugador() {
        return new JugadorAutenticado(JUGADOR, APODO);
    }

    private static HeroeDeCombate sombra() {
        return HeroeDeCombate.aPleno("heroe-7", "Sombra de Vael", 120);
    }

    /** Inventario de mentira que responde siempre lo mismo y cuenta las visitas. */
    private static final class InventarioQueResponde implements HeroeDelJugador {

        private final EstadoDelHeroe respuesta;
        private int consultas;
        private JugadorAutenticado ultimoConsultado;

        private InventarioQueResponde(EstadoDelHeroe respuesta) {
            this.respuesta = respuesta;
        }

        @Override
        public EstadoDelHeroe consultar(JugadorAutenticado jugador) {
            consultas++;
            ultimoConsultado = jugador;
            return respuesta;
        }
    }

    private Sala salaCon(int recompensa) {
        Sala sala = Sala.crear(
                new ParametrosDeSala(6, Modalidad.HASTA_SEIS, recompensa, false, false, null),
                UUID.randomUUID());
        return repositorio.guardar(sala);
    }

    private VerificarHeroe casoDeUso(EstadoDelHeroe respuesta) {
        return new VerificarHeroe(repositorio, new InventarioQueResponde(respuesta));
    }

    @Nested
    @DisplayName("con heroe equipado y libre")
    class HeroeDisponible {

        @Test
        @DisplayName("admite la participacion y devuelve el heroe que ira al combate")
        void admiteLaParticipacion() {
            Sala sala = salaCon(0);
            HeroeDeCombate heroe = sombra();

            VerificacionDeIngreso verificacion =
                    casoDeUso(EstadoDelHeroe.disponible(heroe)).ejecutar(sala.id(), jugador());

            assertAll(
                    () -> assertEquals(ResultadoVerificacion.DISPONIBLE, verificacion.resultado()),
                    () -> assertTrue(verificacion.puedeIngresar()),
                    () -> assertSame(heroe, verificacion.heroe()),
                    () -> assertNull(verificacion.salaQueLoOcupa()));
        }

        @Test
        @DisplayName("informa la recompensa de la sala, que es dato propio")
        void informaLaRecompensa() {
            Sala sala = salaCon(320);

            VerificacionDeIngreso verificacion =
                    casoDeUso(EstadoDelHeroe.disponible(sombra())).ejecutar(sala.id(), jugador());

            assertEquals(320, verificacion.creditosRequeridos());
        }

        @Test
        @DisplayName("no dice cuanto saldo hay: este servicio no lo sabe ni lo adivina")
        void noInventaElSaldo() {
            Sala sala = salaCon(320);

            VerificacionDeIngreso verificacion =
                    casoDeUso(EstadoDelHeroe.disponible(sombra())).ejecutar(sala.id(), jugador());

            assertNull(verificacion.creditosDisponibles());
        }

        @Test
        @DisplayName("no mete al jugador en la sala: la verificacion no tiene efectos")
        void noTieneEfectos() {
            Sala sala = salaCon(0);
            int ocupacionAntes = sala.ocupacion();

            casoDeUso(EstadoDelHeroe.disponible(sombra())).ejecutar(sala.id(), jugador());

            assertEquals(ocupacionAntes,
                    repositorio.buscarPorId(sala.id()).orElseThrow().ocupacion());
        }
    }

    @Nested
    @DisplayName("cuando el inventario rechaza")
    class InventarioRechaza {

        @Test
        @DisplayName("sin heroe equipado no se puede entrar, y el motivo viaja (CA-1)")
        void sinHeroeEquipado() {
            Sala sala = salaCon(0);

            VerificacionDeIngreso verificacion =
                    casoDeUso(EstadoDelHeroe.sinHeroeEquipado()).ejecutar(sala.id(), jugador());

            assertAll(
                    () -> assertEquals(ResultadoVerificacion.SIN_HEROE_EQUIPADO, verificacion.resultado()),
                    () -> assertFalse(verificacion.puedeIngresar()),
                    () -> assertNull(verificacion.heroe()));
        }

        @Test
        @DisplayName("un heroe ocupado nombra donde esta, porque el dialogo lo dice")
        void heroeOcupadoNombraLaActividad() {
            Sala sala = salaCon(0);
            HeroeDeCombate heroe = sombra();

            VerificacionDeIngreso verificacion = casoDeUso(
                    EstadoDelHeroe.ocupado(heroe, "una subasta en curso"))
                    .ejecutar(sala.id(), jugador());

            assertAll(
                    () -> assertEquals(ResultadoVerificacion.HEROE_OCUPADO, verificacion.resultado()),
                    () -> assertFalse(verificacion.puedeIngresar()),
                    () -> assertSame(heroe, verificacion.heroe()),
                    () -> assertEquals("una subasta en curso", verificacion.salaQueLoOcupa()));
        }

        @Test
        @DisplayName("si el inventario no responde, el error sube: no se inventa un veredicto")
        void sinRespuestaNoSeInventaNada() {
            Sala sala = salaCon(0);
            VerificarHeroe casoDeUso = new VerificarHeroe(repositorio, jugador -> {
                throw new InventarioNoDisponible("apagado");
            });

            assertThrows(InventarioNoDisponible.class,
                    () -> casoDeUso.ejecutar(sala.id(), jugador()));
        }
    }

    @Test
    @DisplayName("una sala que no existe es 404, no un dialogo sobre una sala fantasma")
    void salaInexistente() {
        VerificarHeroe casoDeUso = casoDeUso(EstadoDelHeroe.disponible(sombra()));

        assertThrows(SalaNoEncontrada.class,
                () -> casoDeUso.ejecutar(UUID.randomUUID(), jugador()));
    }

    @Test
    @DisplayName("pregunta al inventario por el jugador autenticado, una sola vez")
    void preguntaPorQuienPregunta() {
        Sala sala = salaCon(0);
        InventarioQueResponde inventario =
                new InventarioQueResponde(EstadoDelHeroe.disponible(sombra()));

        new VerificarHeroe(repositorio, inventario).ejecutar(sala.id(), jugador());

        assertAll(
                () -> assertEquals(1, inventario.consultas),
                () -> assertEquals(JUGADOR, inventario.ultimoConsultado.id()),
                () -> assertEquals(APODO, inventario.ultimoConsultado.apodo()));
    }
}
