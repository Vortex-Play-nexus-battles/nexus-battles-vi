package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoVerificacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * La regla de admision por heroe, probada por si misma — HU-SAL-003 / SCRUM-1074.
 *
 * <p><b>Que anade esto.</b> La regla YA estaba cubierta: los tres casos de uso
 * que la llaman —crear sala, ingresar e iniciar partida— la ejercitan de rebote,
 * y por eso no aparece en el informe de JaCoCo como linea sin cubrir. Lo que no
 * habia era una prueba que dijera cual es la regla.
 *
 * <p>La diferencia importa el dia que alguien la cambie: hoy, relajarla haria
 * fallar unas cuantas pruebas de tres clases distintas y el mensaje diria «no se
 * pudo crear la sala», no «la puerta deja pasar a quien no debe». Aqui falla una
 * sola y dice exactamente que se rompio.
 *
 * <p>Tambien fija lo que la puerta <b>no</b> hace: no traduce el fallo del
 * inventario. Un inventario caido no es un heroe no disponible, y confundirlos
 * le diria al jugador que su heroe esta ocupado cuando lo que pasa es que el
 * servicio de al lado no responde.
 */
@DisplayName("PuertaDeHeroe · la regla unica de admision por heroe")
class PuertaDeHeroeReglaTest {

    private static final JugadorAutenticado ANA =
            new JugadorAutenticado(UUID.fromString("11111111-1111-1111-1111-111111111111"), "Ana");

    private static HeroeDeCombate arquero() {
        return new HeroeDeCombate("h-1", "Arquero del Norte", null, 5, 100, 100);
    }

    @Test
    @DisplayName("un heroe disponible pasa, y devuelve el estado tal cual lo dio el inventario")
    void elHeroeDisponiblePasa() {
        EstadoDelHeroe delInventario = EstadoDelHeroe.disponible(arquero());

        EstadoDelHeroe devuelto = PuertaDeHeroe.comprobar(jugador -> delInventario, ANA);

        // Mismo objeto: la puerta no reconstruye ni recorta lo que le dieron. El
        // heroe que sale de aqui es el que se guarda en la ficha del participante.
        assertSame(delInventario, devuelto);
    }

    @Test
    @DisplayName("sin heroe equipado no se pasa, y el error dice por que")
    void sinHeroeEquipadoNoSePasa() {
        HeroeNoDisponible rechazo = assertThrows(HeroeNoDisponible.class,
                () -> PuertaDeHeroe.comprobar(jugador -> EstadoDelHeroe.sinHeroeEquipado(), ANA));

        assertAll(
                () -> assertEquals(422, rechazo.estado()),
                () -> assertEquals(ResultadoVerificacion.SIN_HEROE_EQUIPADO, rechazo.resultado()),
                () -> assertEquals(HeroeNoDisponible.SIN_EQUIPAR, rechazo.tipo()));
    }

    @Test
    @DisplayName("un heroe ocupado en otra sala tampoco pasa, y se nombra donde esta")
    void elHeroeOcupadoNoSePasa() {
        EstadoDelHeroe ocupado = EstadoDelHeroe.ocupado(arquero(), "Sala de Bruno");

        HeroeNoDisponible rechazo = assertThrows(HeroeNoDisponible.class,
                () -> PuertaDeHeroe.comprobar(jugador -> ocupado, ANA));

        assertAll(
                () -> assertEquals(ResultadoVerificacion.HEROE_OCUPADO, rechazo.resultado()),
                () -> assertEquals(HeroeNoDisponible.OCUPADO, rechazo.tipo()),
                // El mensaje nombra el heroe y donde esta: sin eso, el jugador no
                // sabe cual de sus heroes soltar.
                () -> org.junit.jupiter.api.Assertions.assertTrue(
                        rechazo.detalle().contains("Arquero del Norte")
                                && rechazo.detalle().contains("Sala de Bruno"),
                        rechazo.detalle()));
    }

    @Test
    @DisplayName("un inventario caido NO se disfraza de heroe no disponible")
    void elInventarioCaidoSePropagaTalCual() {
        // Son dos problemas distintos y el jugador tiene que poder distinguirlos:
        // uno se arregla equipando un heroe, el otro esperando.
        assertThrows(InventarioNoDisponible.class,
                () -> PuertaDeHeroe.comprobar(jugador -> {
                    throw new InventarioNoDisponible("sin respuesta");
                }, ANA));
    }

    @Test
    @DisplayName("se pregunta por el jugador que se recibe, no por otro")
    void preguntaPorElJugadorCorrecto() {
        JugadorAutenticado[] preguntado = new JugadorAutenticado[1];

        PuertaDeHeroe.comprobar(jugador -> {
            preguntado[0] = jugador;
            return EstadoDelHeroe.disponible(arquero());
        }, ANA);

        assertEquals(ANA, preguntado[0]);
    }
}
