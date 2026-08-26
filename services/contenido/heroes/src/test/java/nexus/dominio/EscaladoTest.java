package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-HER-008 — Escalado de estadisticas por nivel.
 * Fuente: heroes.md seccion 6.1.1, p. 26: el nivel "actua como factor
 * multiplicador en las demas estadisticas", con el ejemplo textual del
 * cliente: "un mago de fuego de nivel 3 posee un ataque base de 30 puntos".
 * Lectura declarada: multiplica las bases; los dados no se escalan (el
 * ejemplo del cliente multiplica el 10 base, no el 1d8).
 */
class EscaladoTest {

    private static Heroe magoFuegoDeNivel(int nivel) {
        Heroe heroe = Heroe.crear(PrototiposIniciales.LISTA.get(2)); // Mago Fuego
        while (heroe.nivel() < nivel) {
            heroe = heroe.ganarExperiencia(Heroe.experienciaRequerida(heroe.nivel()));
        }
        return heroe;
    }

    @Test
    @DisplayName("el ejemplo textual del cliente: mago de fuego nivel 3, ataque base 30")
    void ejemploDelCliente() {
        Estadisticas escaladas = magoFuegoDeNivel(3).estadisticasActuales();
        assertEquals(30, escaladas.ataque().base());
        assertEquals("30 + 1d8", escaladas.ataque().texto());
        assertEquals(8, escaladas.ataque().carasDado(), "el dado no se escala");
    }

    @Test
    @DisplayName("en nivel 1 las estadisticas actuales son las de la Tabla 6")
    void nivel1SinCambios() {
        Heroe heroe = magoFuegoDeNivel(1);
        assertEquals(heroe.prototipo().estadisticasNivel1(), heroe.estadisticasActuales());
    }

    @Test
    @DisplayName("el nivel multiplica poder, vida y defensa")
    void multiplicaLasDemasEstadisticas() {
        Estadisticas nivel3 = magoFuegoDeNivel(3).estadisticasActuales();
        assertEquals(8 * 3, nivel3.poder());
        assertEquals(40 * 3, nivel3.vida());
        assertEquals(10 * 3, nivel3.defensa());
    }

    @Test
    @DisplayName("el escalado tambien aplica a la estadistica Sanar de los sanadores")
    void escalaElSanar() {
        Heroe chaman = Heroe.crear(PrototiposIniciales.LISTA.get(6)).ganarExperiencia(100); // nivel 2
        Estadisticas escaladas = chaman.estadisticasActuales();
        assertEquals(12, escaladas.sanar().base()); // 6 x 2
        assertEquals("12 + 1d6", escaladas.sanar().texto());
    }
}
