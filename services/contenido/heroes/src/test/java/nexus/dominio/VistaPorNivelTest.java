package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Las reglas de progresion como funciones puras de (prototipo, nivel), para
 * exponerlas por API a los servicios que las consumen (motor, misiones,
 * inventario) sin que ninguno tenga que reimplementarlas. Fuentes: seccion
 * 6.1.1 (nivel 1 a 8, experiencia 100 x 1,2^(N-1)) y RC-01 (desbloqueo 1/4/8).
 */
class VistaPorNivelTest {

    private static final Prototipo MAGO_FUEGO = PrototiposIniciales.LISTA.get(2);

    @Test
    @DisplayName("un heroe se puede instanciar directamente en un nivel dado")
    void instanciaEnUnNivel() {
        Heroe heroe = Heroe.deNivel(MAGO_FUEGO, 3);
        assertEquals(3, heroe.nivel());
        assertEquals(0, heroe.experiencia());
        assertEquals(30, heroe.estadisticasActuales().ataque().base());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9, -1})
    @DisplayName("fuera de 1..8 el nivel se rechaza con un mensaje apto para el usuario")
    void nivelFueraDeRango(int nivel) {
        NivelFueraDeRangoException error =
                assertThrows(NivelFueraDeRangoException.class, () -> Heroe.deNivel(MAGO_FUEGO, nivel));
        assertEquals("El nivel de un héroe está entre 1 y 8.", error.getMessage());
    }

    @Test
    @DisplayName("progresar aplica la formula con sobrante y puede encadenar niveles")
    void progresarEncadenaNiveles() {
        // 250 puntos desde nivel 1: cubre 100 (n1) y 120 (n2); sobran 30
        Heroe.Progreso progreso = Heroe.progresar(1, 0, 250);
        assertEquals(3, progreso.nivel());
        assertEquals(30, progreso.experiencia(), 0.0001);
    }

    @Test
    @DisplayName("progresar respeta el tope del nivel 8")
    void progresarRespetaElTope() {
        Heroe.Progreso progreso = Heroe.progresar(8, 10, 100000);
        assertEquals(8, progreso.nivel());
        assertEquals(100010, progreso.experiencia(), 0.0001);
    }

    @Test
    @DisplayName("la experiencia para subir es null en el nivel maximo")
    void experienciaParaSubirEnElTope() {
        assertEquals(100, Heroe.experienciaParaSubirDesde(1));
        assertEquals(120, Heroe.experienciaParaSubirDesde(2), 0.0001);
        assertNull(Heroe.experienciaParaSubirDesde(8));
    }
}
