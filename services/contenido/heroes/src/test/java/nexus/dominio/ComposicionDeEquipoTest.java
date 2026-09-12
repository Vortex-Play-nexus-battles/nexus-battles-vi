package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-JUE-009: "ningun equipo pueda formarse solo con sanadores". Regla del
 * cliente dictada en clase el 2026-07-29 (RG-054 / RC-08): "solo puede haber
 * un healer por grupo. No pueden ser todos healers. Un chaman o un medico, un
 * sanador". El ERS la recoge en CU-44, paso 5 y excepcion E4.
 *
 * El enfrentamiento individual (1v1) se rige por un parametro propio
 * (criterio 3 de la historia): el cliente sostuvo tres posiciones sucesivas
 * (V-09 / PD-002) y la ultima registrada es RC-09, "los sanadores solo
 * participan en combate por equipos".
 */
class ComposicionDeEquipoTest {

    private static final Catalogo CATALOGO = Catalogo.conPrototiposIniciales();

    private static List<Prototipo> equipo(String... nombres) {
        return java.util.Arrays.stream(nombres).map(CATALOGO::fichaDe).toList();
    }

    @Test
    @DisplayName("un equipo con un solo sanador se acepta (RC-08)")
    void unSanadorSeAcepta() {
        ComposicionDeEquipo.Veredicto v = ComposicionDeEquipo.validar(
                equipo("Chamán", "Guerrero Tanque", "Mago Fuego"), false);

        assertTrue(v.valida());
        assertNull(v.motivo());
        assertEquals(1, v.sanadores());
        assertFalse(v.individual());
    }

    @Test
    @DisplayName("un equipo con dos sanadores se rechaza con un mensaje apto para el jugador (RC-08, ERS CU-44 E4)")
    void dosSanadoresSeRechazan() {
        ComposicionDeEquipo.Veredicto v = ComposicionDeEquipo.validar(
                equipo("Chamán", "Médico", "Guerrero Armas"), false);

        assertFalse(v.valida());
        assertEquals(2, v.sanadores());
        assertEquals("Un equipo admite un único sanador: Chamán o Médico.", v.motivo());
    }

    @Test
    @DisplayName("un equipo sin sanadores se acepta")
    void sinSanadoresSeAcepta() {
        ComposicionDeEquipo.Veredicto v = ComposicionDeEquipo.validar(
                equipo("Guerrero Tanque", "Pícaro Veneno"), false);

        assertTrue(v.valida());
        assertEquals(0, v.sanadores());
    }

    @Test
    @DisplayName("en un enfrentamiento individual el sanador se rige por el parametro propio: permitido (criterio 3)")
    void individualConSanadorPermitido() {
        ComposicionDeEquipo.Veredicto v = ComposicionDeEquipo.validar(equipo("Médico"), true);

        assertTrue(v.valida());
        assertTrue(v.individual());
        assertEquals(1, v.sanadores());
    }

    @Test
    @DisplayName("en un enfrentamiento individual el sanador se rige por el parametro propio: no permitido (RC-09)")
    void individualConSanadorNoPermitido() {
        ComposicionDeEquipo.Veredicto v = ComposicionDeEquipo.validar(equipo("Médico"), false);

        assertFalse(v.valida());
        assertTrue(v.individual());
        assertEquals("Los sanadores solo participan en combate por equipos.", v.motivo());
    }

    @Test
    @DisplayName("un individual sin sanador es valido sin importar el parametro")
    void individualSinSanador() {
        assertTrue(ComposicionDeEquipo.validar(equipo("Mago Hielo"), false).valida());
        assertTrue(ComposicionDeEquipo.validar(equipo("Mago Hielo"), true).valida());
    }

    @Test
    @DisplayName("el parametro del individual no relaja la regla del sanador unico en equipos")
    void elParametroNoAplicaAEquipos() {
        ComposicionDeEquipo.Veredicto v = ComposicionDeEquipo.validar(equipo("Chamán", "Médico"), true);

        assertFalse(v.valida());
        assertFalse(v.individual());
    }

    @Test
    @DisplayName("un equipo vacio no es una composicion")
    void equipoVacioRechazado() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ComposicionDeEquipo.validar(List.of(), false));

        assertEquals("Un equipo tiene al menos un héroe.", e.getMessage());
    }
}
