package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-HER-007 — Resolucion de acciones especiales.
 * Fuente: habilidades-y-mejoras.md seccion 6.1.2, p. 28: las acciones "solo
 * aplican en el turno en que se ejecutan", "tienen un turno de carga" y "son
 * afectadas por el multiplicador asociado al nivel".
 */
class ResolucionDeAccionesTest {

    // Criterio 1: el efecto solo rige durante el turno en que fue ejecutada.
    @Test
    @DisplayName("el efecto aplica en el turno de ejecucion y deja de aplicar al avanzar")
    void efectoSoloEnSuTurno() {
        ControlDeRecarga accion = ControlDeRecarga.paraAccionEspecial();
        accion.registrarUso(3);
        assertTrue(accion.efectoVigenteEn(3));
        assertFalse(accion.efectoVigenteEn(4));
    }

    // Criterio 2: reutilizarla dentro del turno de carga se rechaza indicando lo restante.
    @Test
    @DisplayName("dentro del turno de carga la accion se rechaza indicando turnos restantes")
    void turnoDeCargaRechazaConTurnosRestantes() {
        ControlDeRecarga accion = ControlDeRecarga.paraAccionEspecial(); // 1 turno de carga
        accion.registrarUso(3);
        assertFalse(accion.disponibleEn(4));
        assertEquals(1, accion.turnosRestantesEn(4));
        assertTrue(accion.disponibleEn(5));
        assertEquals(0, accion.turnosRestantesEn(5));
    }

    @Test
    @DisplayName("una accion nunca usada esta disponible desde el primer turno")
    void nuncaUsadaDisponible() {
        assertTrue(ControlDeRecarga.paraAccionEspecial().disponibleEn(1));
    }

    // Criterio 3: el efecto se multiplica por el nivel del heroe.
    @Test
    @DisplayName("el multiplicador del efecto es el nivel del heroe")
    void multiplicadorDeEfectoEsElNivel() {
        Heroe nivel1 = Heroe.crear(PrototiposIniciales.LISTA.get(0));
        Heroe nivel3 = nivel1.ganarExperiencia(250); // llega a nivel 3 (ver ProgresionTest)
        assertEquals(1, nivel1.multiplicadorDeEfecto());
        assertEquals(3, nivel3.multiplicadorDeEfecto());
    }
}
