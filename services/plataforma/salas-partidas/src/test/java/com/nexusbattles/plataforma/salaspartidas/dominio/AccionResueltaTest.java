package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta.Accion;
import com.nexusbattles.plataforma.salaspartidas.dominio.AccionResuelta.Afectado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resultado de una accion de combate — HU-SAL-005.
 *
 * <p>Se prueban los limites que fija el mensaje {@code AccionResuelta} del
 * AsyncAPI, no reglas de juego: este servicio no calcula dano.
 */
@DisplayName("AccionResuelta · el hecho que mueve las barras (HU-SAL-005)")
class AccionResueltaTest {

    private static final UUID PARTIDA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ANA = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRUNO = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final Accion GOLPE = new Accion("GOLPE", "Golpe", null);

    @Test
    @DisplayName("lleva vida actual y vida maxima de cada afectado, nunca un color")
    void llevaVidaNumerica() {
        AccionResuelta resultado = new AccionResuelta(PARTIDA, ANA, GOLPE,
                List.of(new Afectado(BRUNO, 55, 100, -25)));

        Afectado bruno = resultado.afectados().get(0);
        assertEquals(55, bruno.vidaActual());
        assertEquals(100, bruno.vidaMaxima());
        assertEquals(-25, bruno.diferencia());
    }

    @Test
    @DisplayName("una accion puede no afectar a nadie: la lista vacia es valida")
    void sinAfectadosEsValida() {
        AccionResuelta fallida = new AccionResuelta(PARTIDA, ANA, GOLPE, List.of());

        assertTrue(fallida.afectados().isEmpty());
    }

    @Test
    @DisplayName("la lista de afectados no se puede alterar despues de construida")
    void losAfectadosSonInmutables() {
        List<Afectado> mutable = new ArrayList<>(List.of(new Afectado(BRUNO, 55, 100, -25)));
        AccionResuelta resultado = new AccionResuelta(PARTIDA, ANA, GOLPE, mutable);

        mutable.clear();

        assertEquals(1, resultado.afectados().size());
        assertThrows(UnsupportedOperationException.class, () -> resultado.afectados().clear());
    }

    @Test
    @DisplayName("el icono de la accion es opcional, como lo declara el contrato")
    void elIconoPuedeFaltar() {
        assertNull(new Accion("GOLPE", "Golpe", null).icono());
        assertEquals("espada", new Accion("GOLPE", "Golpe", "espada").icono());
    }

    @Test
    @DisplayName("codigo y nombre de la accion son obligatorios")
    void codigoYNombreObligatorios() {
        assertThrows(IllegalArgumentException.class, () -> new Accion(null, "Golpe", null));
        assertThrows(IllegalArgumentException.class, () -> new Accion(" ", "Golpe", null));
        assertThrows(IllegalArgumentException.class, () -> new Accion("GOLPE", "", null));
    }

    @Test
    @DisplayName("la vida actual no baja de cero y la maxima es al menos uno (minimos del contrato)")
    void limitesDeVida() {
        assertThrows(IllegalArgumentException.class, () -> new Afectado(BRUNO, -1, 100, -101));
        assertThrows(IllegalArgumentException.class, () -> new Afectado(BRUNO, 0, 0, 0));
        // El cero exacto es una barra vacia, no un error: es como se ve un heroe caido.
        assertEquals(0, new Afectado(BRUNO, 0, 100, -100).vidaActual());
    }

    @Test
    @DisplayName("sin partida, ejecutor, accion o lista de afectados no hay resultado")
    void camposObligatorios() {
        List<Afectado> afectados = List.of(new Afectado(BRUNO, 55, 100, -25));
        assertThrows(NullPointerException.class,
                () -> new AccionResuelta(null, ANA, GOLPE, afectados));
        assertThrows(NullPointerException.class,
                () -> new AccionResuelta(PARTIDA, null, GOLPE, afectados));
        assertThrows(NullPointerException.class,
                () -> new AccionResuelta(PARTIDA, ANA, null, afectados));
        assertThrows(NullPointerException.class,
                () -> new AccionResuelta(PARTIDA, ANA, GOLPE, null));
        assertThrows(NullPointerException.class, () -> new Afectado(null, 55, 100, -25));
    }
}
