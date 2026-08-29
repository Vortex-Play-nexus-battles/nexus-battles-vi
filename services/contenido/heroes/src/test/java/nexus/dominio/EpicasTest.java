package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * HU-HER-009 — Uso de habilidades epicas y HU-HER-010 — Bonificacion por tipo.
 * Fuente: epicas.md seccion 6.1.2, p. 32 (Tabla 20): las epicas "no usan
 * puntos de poder", "tienen dos turnos de recarga", otorgan un efecto para
 * todos los heroes y un efecto epico solo para el tipo de heroe afin.
 */
class EpicasTest {

    // ---- HU-HER-009 — uso ----

    @Test
    @DisplayName("las ocho epicas de la Tabla 20 estan cargadas")
    void ochoEpicasCargadas() {
        assertEquals(8, EpicasIniciales.LISTA.size());
    }

    @Test
    @DisplayName("usar una epica no consume poder")
    void usarEpicaNoConsumePoder() {
        EstadoDePoder poder = new EstadoDePoder(6, 10);
        ControlDeRecarga epica = ControlDeRecarga.paraEpica();
        epica.registrarUso(1);
        assertEquals(6, poder.actual(), "el poder no participa en el uso de la epica");
    }

    @Test
    @DisplayName("la epica tiene dos turnos de recarga")
    void dosTurnosDeRecarga() {
        ControlDeRecarga epica = ControlDeRecarga.paraEpica();
        epica.registrarUso(1);
        assertFalse(epica.disponibleEn(2));
        assertFalse(epica.disponibleEn(3));
        assertTrue(epica.disponibleEn(4));
        assertEquals(2, epica.turnosRestantesEn(2));
        assertEquals(1, epica.turnosRestantesEn(3));
    }

    // ---- HU-HER-010 — bonificacion por tipo ----

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Guerrero Tanque | Golpe de defensa",
            "Guerrero Armas  | Segundo impulso",
            "Mago Fuego      | Luz cegadora",
            "Mago Hielo      | Frio concentrado",
            "Pícaro Veneno   | Toma y lleva",
            "Pícaro Machete  | Intimidación sangrienta",
            "Chamán          | Té changua",
            "Médico          | Reanimador 3000"})
    @DisplayName("cada tipo de heroe tiene su epica afin de la Tabla 20")
    void epicaAfinPorTipo(String prototipo, String epica) {
        assertEquals(epica.trim(), EpicasIniciales.afinA(prototipo.trim()).nombre());
    }

    @Test
    @DisplayName("el heroe afin recibe el efecto general y el potenciado")
    void heroeAfinRecibeAmbosEfectos() {
        // Ejemplo textual del documento: Segundo Impulso con un Guerrero Armas.
        Epica segundoImpulso = EpicasIniciales.afinA("Guerrero Armas");
        Epica.Efectos efectos = segundoImpulso.efectosPara(PrototiposIniciales.LISTA.get(1)); // Guerrero Armas
        assertEquals("Recupera 1d4 de vida", efectos.general());
        assertEquals("+3 a la vida, +5% de crítico", efectos.potenciado());
    }

    @Test
    @DisplayName("un heroe no afin solo recibe el efecto general")
    void heroeNoAfinSoloGeneral() {
        Epica segundoImpulso = EpicasIniciales.afinA("Guerrero Armas");
        Epica.Efectos efectos = segundoImpulso.efectosPara(PrototiposIniciales.LISTA.get(2)); // Mago Fuego
        assertEquals("Recupera 1d4 de vida", efectos.general());
        assertNull(efectos.potenciado());
    }

    @Test
    @DisplayName("las epicas de los sanadores no tienen efecto general (Tabla 20: No aplica)")
    void epicasDeSanadoresSinEfectoGeneral() {
        Epica teChangua = EpicasIniciales.afinA("Chamán");
        Epica.Efectos paraOtro = teChangua.efectosPara(PrototiposIniciales.LISTA.get(0));
        assertNull(paraOtro.general());
        assertNull(paraOtro.potenciado());
        Epica.Efectos paraChaman = teChangua.efectosPara(PrototiposIniciales.LISTA.get(6));
        assertEquals("Sana a todos +(4d8)", paraChaman.potenciado());
    }
}
