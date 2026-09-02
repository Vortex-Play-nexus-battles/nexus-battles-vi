package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TablaEfectosTest {

    private static final List<CategoriaEfecto> ORDEN_ESPERADO = List.of(
            CategoriaEfecto.CAUSAR_DANO,
            CategoriaEfecto.CAUSAR_DANO_CRITICO,
            CategoriaEfecto.EVADIR_EL_GOLPE,
            CategoriaEfecto.RESISTIR_EL_GOLPE,
            CategoriaEfecto.ESCAPAR_AL_GOLPE,
            CategoriaEfecto.SIN_EFECTO);

    @Test
    @DisplayName("la tabla recalculada conserva exactamente ocho mil filas")
    void conservaOchoMilFilas() {
        DistribucionEfectos recalculada = DistribucionEfectos.GUERRERO_ARMAS
                .aplicarEquipamiento(List.of(
                        new EfectoEquipamiento(CategoriaEfecto.CAUSAR_DANO, 4)));

        TablaEfectos tabla = TablaEfectos.desde(recalculada);

        assertEquals(8_000, tabla.totalFilas());
        assertEquals(5_120, tabla.filasDe(CategoriaEfecto.CAUSAR_DANO));
        assertEquals(2_080, tabla.filasDe(CategoriaEfecto.SIN_EFECTO));
    }

    @Test
    @DisplayName("el incremento mueve filas desde sin efecto a la categoria beneficiada")
    void mueveLasFilasDelIncremento() {
        TablaEfectos original = TablaEfectos.desde(DistribucionEfectos.GUERRERO_TANQUE);
        TablaEfectos equipada = TablaEfectos.desde(
                DistribucionEfectos.GUERRERO_TANQUE.aplicarEquipamiento(List.of(
                        new EfectoEquipamiento(CategoriaEfecto.RESISTIR_EL_GOLPE, 5))));

        assertEquals(
                original.filasDe(CategoriaEfecto.RESISTIR_EL_GOLPE) + 400,
                equipada.filasDe(CategoriaEfecto.RESISTIR_EL_GOLPE));
        assertEquals(
                original.filasDe(CategoriaEfecto.SIN_EFECTO) - 400,
                equipada.filasDe(CategoriaEfecto.SIN_EFECTO));
    }

    @Test
    @DisplayName("la tabla conserva el orden de efectos usado por el personaje")
    void conservaElOrden() {
        TablaEfectos tabla = TablaEfectos.desde(DistribucionEfectos.PICARO_MACHETE);

        assertEquals(ORDEN_ESPERADO, tabla.orden());
    }

    @Test
    @DisplayName("el selector respeta los limites recalculados sin alterar el orden")
    void seleccionaConLaTablaRecalculada() {
        DistribucionEfectos recalculada = DistribucionEfectos.GUERRERO_ARMAS
                .aplicarEquipamiento(List.of(
                        new EfectoEquipamiento(CategoriaEfecto.CAUSAR_DANO, 4)));

        assertEquals(CategoriaEfecto.CAUSAR_DANO,
                SelectorEfecto.seleccionar(5_120, recalculada));
        assertEquals(CategoriaEfecto.CAUSAR_DANO_CRITICO,
                SelectorEfecto.seleccionar(5_121, recalculada));
        assertEquals(CategoriaEfecto.SIN_EFECTO,
                SelectorEfecto.seleccionar(8_000, recalculada));
    }

    @Test
    @DisplayName("rechaza indices fuera de las ocho mil filas")
    void rechazaIndicesFueraDeLaTabla() {
        TablaEfectos tabla = TablaEfectos.desde(DistribucionEfectos.MAGO_HIELO);

        assertThrows(IllegalArgumentException.class, () -> tabla.efectoEn(0));
        assertThrows(IllegalArgumentException.class, () -> tabla.efectoEn(8_001));
    }
}
