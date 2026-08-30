package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * RC-01 (RG-021, dictada por el cliente el 2026-07-29 y ausente de las tablas
 * del PDF): "Las habilidades de cada heroe se desbloquean en los niveles 1, 4
 * y 8. El heroe nace con la primera, aprende la segunda en nivel 4 y la
 * tercera en nivel 8. Aplica a todas las clases."
 * Cita: "Uno, cuatro y ocho, porque el le da subir de nivel, gana poderes.
 * Escribanlo en general."
 * El orden de desbloqueo es el orden de la Tabla 7 (primera/segunda/tercera).
 */
class DesbloqueoDeAccionesTest {

    private static Heroe heroeDeNivel(int nivel) {
        Heroe heroe = Heroe.crear(PrototiposIniciales.LISTA.get(0));
        while (heroe.nivel() < nivel) {
            heroe = heroe.ganarExperiencia(Heroe.experienciaRequerida(heroe.nivel()));
        }
        return heroe;
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1",
            "2, 1",
            "3, 1",
            "4, 2",
            "5, 2",
            "7, 2",
            "8, 3"})
    @DisplayName("las acciones se desbloquean en los niveles 1, 4 y 8")
    void accionesDesbloqueadasSegunNivel(int nivel, int accionesEsperadas) {
        assertEquals(accionesEsperadas, heroeDeNivel(nivel).accionesDisponibles().size());
    }

    @Test
    @DisplayName("el heroe nace con la primera accion de su tabla")
    void naceConLaPrimeraAccion() {
        Heroe heroe = heroeDeNivel(1);
        assertEquals(1, heroe.accionesDisponibles().size());
        assertEquals("Golpe con escudo", heroe.accionesDisponibles().get(0).nombre());
    }

    @Test
    @DisplayName("en nivel 8 dispone de las tres acciones en el orden de la Tabla 7")
    void enNivel8TieneLasTres() {
        Heroe heroe = heroeDeNivel(8);
        assertEquals(3, heroe.accionesDisponibles().size());
        assertEquals(heroe.prototipo().acciones(), heroe.accionesDisponibles());
    }
}
