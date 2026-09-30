package nexus.combate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La Tabla 21 esta escrita por TIPO DE HEROE, y el tipo de heroe es el
 * prototipo del catalogo: no hacia falta un mapeo inventado. Y la Tabla 23:
 * «todo efecto que aumente en valor restara a no causar dano».
 */
class DistribucionPorPrototipoTest {

    @ParameterizedTest
    @CsvSource({
            "Guerrero Tanque, 40, 0, 5, 0, 5, 50",
            "Guerrero Armas, 60, 5, 3, 0, 2, 30",
            "Mago Fuego, 70, 5, 0, 5, 0, 20",
            "Mago Hielo, 70, 6, 0, 4, 0, 20",
            "Pícaro Veneno, 55, 10, 0, 0, 0, 35",
            "Pícaro Machete, 60, 8, 0, 0, 2, 30"})
    @DisplayName("cada prototipo del catalogo tiene su fila de la Tabla 21")
    void filaDeLaTabla21(String prototipo, int dano, int critico, int evadir, int resistir,
                         int escapar, int sinEfecto) {
        DistribucionEfectos reparto = DistribucionEfectos.dePrototipo(prototipo).orElseThrow();
        assertEquals(new DistribucionEfectos(dano, critico, evadir, resistir, escapar, sinEfecto), reparto);
    }

    @Test
    @DisplayName("la busqueda tolera tildes y mayusculas, como el catalogo de heroes")
    void toleranteATildes() {
        assertEquals(DistribucionEfectos.PICARO_VENENO,
                DistribucionEfectos.dePrototipo("picaro veneno").orElseThrow());
        assertEquals(DistribucionEfectos.GUERRERO_TANQUE,
                DistribucionEfectos.dePrototipo("  GUERRERO TANQUE ").orElseThrow());
    }

    @Test
    @DisplayName("los sanadores no tienen fila: la Tabla 21 les da 0 % porque no atacan")
    void sanadoresSinFila() {
        assertTrue(DistribucionEfectos.dePrototipo("Chamán").isEmpty());
        assertTrue(DistribucionEfectos.dePrototipo("Médico").isEmpty());
        assertTrue(DistribucionEfectos.dePrototipo("Nigromante").isEmpty());
        assertTrue(DistribucionEfectos.dePrototipo(null).isEmpty());
    }

    @Test
    @DisplayName("Tabla 23: +6 de critico al Guerrero Armas deja 11 de critico y 24 sin efecto")
    void tabla23() {
        DistribucionEfectos equipado = DistribucionEfectos.GUERRERO_ARMAS.ajustarCritico(6);
        assertEquals(11, equipado.causarDanoCritico());
        assertEquals(24, equipado.sinEfecto());
        TablaEfectos tabla = TablaEfectos.desde(equipado);
        assertEquals(880, tabla.filasDe(CategoriaEfecto.CAUSAR_DANO_CRITICO));
        assertEquals(1920, tabla.filasDe(CategoriaEfecto.SIN_EFECTO));
        assertEquals(CategoriaEfecto.CAUSAR_DANO_CRITICO, tabla.efectoEn(4801));
        assertEquals(CategoriaEfecto.CAUSAR_DANO_CRITICO, tabla.efectoEn(5680));
        assertEquals(CategoriaEfecto.EVADIR_EL_GOLPE, tabla.efectoEn(5681));
        assertEquals(CategoriaEfecto.SIN_EFECTO, tabla.efectoEn(6081));
    }

    @Test
    @DisplayName("restar critico (Baculo de Permafrost al que ataca) devuelve las filas a «no causar dano»")
    void restarCritico() {
        DistribucionEfectos menos = DistribucionEfectos.GUERRERO_ARMAS.ajustarCritico(-2);
        assertEquals(3, menos.causarDanoCritico());
        assertEquals(32, menos.sinEfecto());
    }

    @Test
    @DisplayName("el ajuste no pasa de lo disponible: ni critico negativo ni «no causar dano» negativo")
    void ajusteAcotado() {
        assertEquals(0, DistribucionEfectos.GUERRERO_TANQUE.ajustarCritico(-5).causarDanoCritico());
        assertEquals(50, DistribucionEfectos.GUERRERO_TANQUE.ajustarCritico(-5).sinEfecto());
        DistribucionEfectos tope = DistribucionEfectos.MAGO_FUEGO.ajustarCritico(40);
        assertEquals(25, tope.causarDanoCritico());
        assertEquals(0, tope.sinEfecto());
        assertEquals(DistribucionEfectos.MAGO_HIELO, DistribucionEfectos.MAGO_HIELO.ajustarCritico(0));
    }
}
