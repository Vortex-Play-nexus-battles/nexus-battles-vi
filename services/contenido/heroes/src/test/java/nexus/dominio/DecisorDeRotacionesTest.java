package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-SIM-002: "que la inteligencia artificial respete el orden de prioridad que
 * le di y ataque igual cuando ninguna rotacion sirve". Seccion 7.8.5, p. 61
 * (RF-MIS-13 a RF-MIS-16): en cada turno se verifica la Rotacion 1 (poder
 * suficiente, cooldown completado, estado de salud); si no es viable, la 2;
 * luego la 3; si ninguna, ataque basico sin consumir poder; y el ciclo se
 * repite en cada turno empezando por la Rotacion 1.
 *
 * Decision de implementacion: cada rotacion es una secuencia y lleva un cursor
 * (el paso que le toca); se evalua el paso que le toca a cada rotacion en su
 * orden de prioridad y se avanza solo el cursor de la rotacion ejecutada.
 * "Estado de salud" no tiene umbral definido en ninguna fuente (pendiente P-E):
 * hipotesis de trabajo, el heroe debe tener vida para actuar.
 */
class DecisorDeRotacionesTest {

    private static final Catalogo CATALOGO = Catalogo.conPrototiposIniciales();

    /** Guerrero Armas nivel 8: poder maximo 64 (8 x 8); Embate 4, Lanza 4, Golpe de tormenta 6. */
    private static EstrategiaDeCombate ejemploDelDocumento() {
        return EstrategiaDeCombate.configurar(Heroe.deNivel(CATALOGO.fichaDe("Guerrero Armas"), 8), List.of(
                List.of("Golpe de tormenta", "Embate sangriento", "Ataque básico"),
                List.of("Lanza de los dioses", "Ataque básico", "Ataque básico"),
                List.of("Embate sangriento", "Ataque básico", "Ataque básico")));
    }

    private static EstadoEnTurno estado(int turno, int poder) {
        return new EstadoEnTurno(turno, poder, 44, Map.of(), List.of(0, 0, 0));
    }

    @Test
    @DisplayName("con poder y sin recargas pendientes se ejecuta el paso que toca de la Rotacion 1")
    void rotacionUnoViable() {
        Decision d = DecisorDeRotaciones.decidir(ejemploDelDocumento(), estado(1, 64));

        assertEquals("Golpe de tormenta", d.accion());
        assertEquals(1, d.rotacion());
        assertEquals(6, d.costoDePoder());
        assertEquals(List.of(1, 0, 0), d.cursoresSiguientes());
        assertEquals(1, d.evaluaciones().size());
        assertTrue(d.evaluaciones().get(0).viable());
    }

    @Test
    @DisplayName("sin poder suficiente para la Rotacion 1 se pasa a la Rotacion 2 (RF-MIS-14)")
    void poderInsuficientePasaALaSiguiente() {
        Decision d = DecisorDeRotaciones.decidir(ejemploDelDocumento(), estado(1, 5));

        assertEquals("Lanza de los dioses", d.accion());
        assertEquals(2, d.rotacion());
        assertEquals(4, d.costoDePoder());
        assertEquals(List.of(0, 1, 0), d.cursoresSiguientes());
        assertFalse(d.evaluaciones().get(0).viable());
        assertEquals("Poder insuficiente: Golpe de tormenta cuesta 6 y el héroe tiene 5.", d.evaluaciones().get(0).razon());
        assertTrue(d.evaluaciones().get(1).viable());
    }

    @Test
    @DisplayName("una habilidad en periodo de espera no es viable hasta que se cumpla la carga (HU-HER-007)")
    void habilidadEnRecarga() {
        EstadoEnTurno enRecarga = new EstadoEnTurno(4, 64, 44, Map.of("Golpe de tormenta", 3), List.of(0, 0, 0));
        Decision d = DecisorDeRotaciones.decidir(ejemploDelDocumento(), enRecarga);

        assertEquals("Lanza de los dioses", d.accion());
        assertEquals("En recarga: Golpe de tormenta se usó en el turno 3 y vuelve a estar disponible en el turno 5.",
                d.evaluaciones().get(0).razon());

        EstadoEnTurno yaDisponible = new EstadoEnTurno(5, 64, 44, Map.of("Golpe de tormenta", 3), List.of(0, 0, 0));
        assertEquals("Golpe de tormenta", DecisorDeRotaciones.decidir(ejemploDelDocumento(), yaDisponible).accion());
    }

    @Test
    @DisplayName("si ninguna rotacion es viable se ejecuta el ataque basico sin consumir poder (RF-MIS-15)")
    void ningunaViableAtaqueBasico() {
        EstrategiaDeCombate soloEspeciales = EstrategiaDeCombate.configurar(
                Heroe.deNivel(CATALOGO.fichaDe("Guerrero Armas"), 8), List.of(
                        List.of("Golpe de tormenta"), List.of("Lanza de los dioses"), List.of("Embate sangriento")));

        Decision d = DecisorDeRotaciones.decidir(soloEspeciales, estado(1, 0));

        assertEquals(EstrategiaDeCombate.ATAQUE_BASICO, d.accion());
        assertNull(d.rotacion());
        assertEquals(0, d.costoDePoder());
        assertEquals(3, d.evaluaciones().size());
        assertEquals(List.of(0, 0, 0), d.cursoresSiguientes());
    }

    @Test
    @DisplayName("en cada turno nuevo se vuelve a evaluar desde la Rotacion 1 (RF-MIS-16)")
    void cadaTurnoEmpiezaPorLaRotacionUno() {
        EstrategiaDeCombate estrategia = ejemploDelDocumento();
        Decision turno1 = DecisorDeRotaciones.decidir(estrategia, estado(1, 5));
        assertEquals(2, turno1.rotacion());

        EstadoEnTurno turno2 = new EstadoEnTurno(2, 64, 44, Map.of("Lanza de los dioses", 1), turno1.cursoresSiguientes());
        Decision d = DecisorDeRotaciones.decidir(estrategia, turno2);

        assertEquals(1, d.rotacion());
        assertEquals("Golpe de tormenta", d.accion());
    }

    @Test
    @DisplayName("el cursor de la rotacion ejecutada avanza al siguiente paso y da la vuelta al terminar la secuencia")
    void elCursorAvanzaYDaLaVuelta() {
        EstrategiaDeCombate estrategia = ejemploDelDocumento();

        Decision segundoPaso = DecisorDeRotaciones.decidir(estrategia,
                new EstadoEnTurno(2, 64, 44, Map.of(), List.of(1, 0, 0)));
        assertEquals("Embate sangriento", segundoPaso.accion());
        assertEquals(List.of(2, 0, 0), segundoPaso.cursoresSiguientes());

        Decision tercerPaso = DecisorDeRotaciones.decidir(estrategia,
                new EstadoEnTurno(3, 64, 44, Map.of(), List.of(2, 0, 0)));
        assertEquals(EstrategiaDeCombate.ATAQUE_BASICO, tercerPaso.accion());
        assertEquals(1, tercerPaso.rotacion());
        assertEquals(0, tercerPaso.costoDePoder());
        assertEquals(List.of(3, 0, 0), tercerPaso.cursoresSiguientes());

        Decision vuelta = DecisorDeRotaciones.decidir(estrategia,
                new EstadoEnTurno(4, 64, 44, Map.of(), List.of(3, 0, 0)));
        assertEquals("Golpe de tormenta", vuelta.accion());
        assertEquals(List.of(4, 0, 0), vuelta.cursoresSiguientes());
    }

    @Test
    @DisplayName("la estrategia por defecto ejecuta el ataque basico sin evaluar rotaciones")
    void estrategiaPorDefecto() {
        EstrategiaDeCombate porDefecto = EstrategiaDeCombate.configurar(
                Heroe.deNivel(CATALOGO.fichaDe("Chamán"), 1), List.of());

        Decision d = DecisorDeRotaciones.decidir(porDefecto, new EstadoEnTurno(1, 10, 28, Map.of(), List.of()));

        assertEquals(EstrategiaDeCombate.ATAQUE_BASICO, d.accion());
        assertNull(d.rotacion());
        assertTrue(d.evaluaciones().isEmpty());
    }

    @Test
    @DisplayName("un heroe sin vida no actua (estado de salud, hipotesis P-E)")
    void sinVidaNoActua() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DecisorDeRotaciones.decidir(ejemploDelDocumento(),
                        new EstadoEnTurno(1, 64, 0, Map.of(), List.of(0, 0, 0))));

        assertEquals("Un héroe sin vida no actúa.", e.getMessage());
    }

    @Test
    @DisplayName("el poder informado no puede superar el maximo del heroe en su nivel")
    void poderFueraDeRango() {
        assertThrows(IllegalArgumentException.class,
                () -> DecisorDeRotaciones.decidir(ejemploDelDocumento(), estado(1, 65)));
    }

    @Test
    @DisplayName("los cursores ausentes se toman como cero (primer paso de cada rotacion)")
    void cursoresAusentes() {
        Decision d = DecisorDeRotaciones.decidir(ejemploDelDocumento(),
                new EstadoEnTurno(1, 64, 44, Map.of(), null));

        assertEquals("Golpe de tormenta", d.accion());
        assertEquals(List.of(1, 0, 0), d.cursoresSiguientes());
    }
}
