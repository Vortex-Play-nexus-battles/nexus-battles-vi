package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-SIM-001: "configurar hasta tres rotaciones de habilidades con prioridad,
 * para decidir la estrategia con la que mi heroe peleara sin mi".
 * Fuente: Proyecto Integrador II, seccion 7.8.5, p. 61 (RF-MIS-12): hasta tres
 * rotaciones con prioridad Alta, Media y Baja; ERS CU-61 paso 4 y excepcion E1:
 * "el sistema valida que las habilidades pertenezcan al heroe seleccionado" y,
 * si no, "rechaza la rotacion e indica las habilidades validas". Sin rotaciones,
 * el comportamiento por defecto es el ataque basico (RF-MIS-15).
 */
class EstrategiaDeCombateTest {

    private static final Catalogo CATALOGO = Catalogo.conPrototiposIniciales();

    private static Heroe heroe(String nombre, int nivel) {
        return Heroe.deNivel(CATALOGO.fichaDe(nombre), nivel);
    }

    @Test
    @DisplayName("el ejemplo del documento: tres rotaciones del Guerrero Armas con prioridad alta, media y baja")
    void ejemploDelDocumento() {
        EstrategiaDeCombate e = EstrategiaDeCombate.configurar(heroe("Guerrero Armas", 8), List.of(
                List.of("Golpe de tormenta", "Embate sangriento", "Ataque básico"),
                List.of("Lanza de los dioses", "Ataque básico", "Ataque básico"),
                List.of("Embate sangriento", "Ataque básico", "Ataque básico")));

        assertEquals(3, e.rotaciones().size());
        assertEquals(Rotacion.Prioridad.ALTA, e.rotaciones().get(0).prioridad());
        assertEquals(Rotacion.Prioridad.MEDIA, e.rotaciones().get(1).prioridad());
        assertEquals(Rotacion.Prioridad.BAJA, e.rotaciones().get(2).prioridad());
        assertEquals(List.of("Golpe de tormenta", "Embate sangriento", "Ataque básico"), e.rotaciones().get(0).pasos());
        assertFalse(e.esPorDefecto());
    }

    @Test
    @DisplayName("una rotacion solo admite habilidades que el heroe posee: la tercera accion no se posee antes del nivel 8 (RC-01)")
    void habilidadNoPoseidaSeRechazaIndicandoLasValidas() {
        RotacionInvalidaException ex = assertThrows(RotacionInvalidaException.class,
                () -> EstrategiaDeCombate.configurar(heroe("Guerrero Armas", 4), List.of(
                        List.of("Golpe de tormenta", "Ataque básico"))));

        assertEquals("La rotación 1 usa una habilidad que Guerrero Armas no posee en nivel 4: Golpe de tormenta.",
                ex.getMessage());
        assertEquals(List.of("Embate sangriento", "Lanza de los dioses", "Ataque básico"), ex.habilidadesValidas());
    }

    @Test
    @DisplayName("una habilidad de otro heroe se rechaza (ERS CU-61 E1)")
    void habilidadDeOtroHeroeSeRechaza() {
        RotacionInvalidaException ex = assertThrows(RotacionInvalidaException.class,
                () -> EstrategiaDeCombate.configurar(heroe("Mago Fuego", 8), List.of(
                        List.of("Misiles de magma", "Golpe con escudo"))));

        assertEquals("La rotación 1 usa una habilidad que Mago Fuego no posee en nivel 8: Golpe con escudo.",
                ex.getMessage());
    }

    @Test
    @DisplayName("los pasos se aceptan como los escribe el jugador y se guardan con el nombre exacto de la Tabla 7")
    void pasosTolerantesYCanonicos() {
        EstrategiaDeCombate e = EstrategiaDeCombate.configurar(heroe("Mago Fuego", 8), List.of(
                List.of("misiles de MAGMA", "vulcano", "ataque basico")));

        assertEquals(List.of("Misiles de magma", "Vulcano", "Ataque básico"), e.rotaciones().get(0).pasos());
    }

    @Test
    @DisplayName("una estrategia admite hasta tres rotaciones")
    void masDeTresRotacionesSeRechazan() {
        List<String> basica = List.of("Ataque básico");
        RotacionInvalidaException ex = assertThrows(RotacionInvalidaException.class,
                () -> EstrategiaDeCombate.configurar(heroe("Chamán", 1), List.of(basica, basica, basica, basica)));

        assertEquals("Una estrategia admite hasta tres rotaciones: alta, media y baja.", ex.getMessage());
    }

    @Test
    @DisplayName("una rotacion sin pasos se rechaza")
    void rotacionVaciaSeRechaza() {
        RotacionInvalidaException ex = assertThrows(RotacionInvalidaException.class,
                () -> EstrategiaDeCombate.configurar(heroe("Chamán", 1), List.of(
                        List.of("Toque de la Vida"), List.of())));

        assertEquals("La rotación 2 no tiene pasos.", ex.getMessage());
    }

    @Test
    @DisplayName("sin rotaciones configuradas el comportamiento por defecto es el ataque basico (RF-MIS-15)")
    void sinRotacionesAtaqueBasico() {
        EstrategiaDeCombate e = EstrategiaDeCombate.configurar(heroe("Pícaro Veneno", 3), List.of());

        assertTrue(e.esPorDefecto());
        assertTrue(e.rotaciones().isEmpty());
        assertEquals("Ataque básico", e.comportamientoPorDefecto());
        assertEquals(EstrategiaDeCombate.ATAQUE_BASICO, e.comportamientoPorDefecto());
    }

    @Test
    @DisplayName("las habilidades validas son las desbloqueadas en el nivel mas el ataque basico")
    void habilidadesValidasSegunNivel() {
        assertEquals(List.of("Golpe con escudo", "Ataque básico"),
                EstrategiaDeCombate.configurar(heroe("Guerrero Tanque", 1), List.of()).habilidadesValidas());
        assertEquals(List.of("Golpe con escudo", "Mano de piedra", "Defensa feroz", "Ataque básico"),
                EstrategiaDeCombate.configurar(heroe("Guerrero Tanque", 8), List.of()).habilidadesValidas());
    }
}
