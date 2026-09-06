package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DistribucionEfectosEquipamientoTest {

    @Test
    @DisplayName("incrementar una categoria descuenta la misma probabilidad de sin efecto")
    void aplicaUnEfecto() {
        DistribucionEfectos base = DistribucionEfectos.GUERRERO_ARMAS;

        DistribucionEfectos recalculada = base.aplicarEquipamiento(List.of(
                new EfectoEquipamiento(CategoriaEfecto.CAUSAR_DANO, 4)));

        assertEquals(new DistribucionEfectos(64, 5, 3, 0, 2, 26), recalculada);
        assertEquals(new DistribucionEfectos(60, 5, 3, 0, 2, 30), base);
    }

    @Test
    @DisplayName("acumula efectos equipados y conserva las categorias no afectadas")
    void aplicaVariosEfectos() {
        DistribucionEfectos recalculada = DistribucionEfectos.GUERRERO_TANQUE
                .aplicarEquipamiento(List.of(
                        new EfectoEquipamiento(CategoriaEfecto.CAUSAR_DANO, 3),
                        new EfectoEquipamiento(CategoriaEfecto.CAUSAR_DANO, 2),
                        new EfectoEquipamiento(CategoriaEfecto.RESISTIR_EL_GOLPE, 5)));

        assertEquals(new DistribucionEfectos(45, 0, 5, 5, 5, 40), recalculada);
    }

    @Test
    @DisplayName("sin equipamiento conserva la distribucion base")
    void conservaLaBaseSinEfectos() {
        assertEquals(
                DistribucionEfectos.MAGO_FUEGO,
                DistribucionEfectos.MAGO_FUEGO.aplicarEquipamiento(List.of()));
    }

    @Test
    @DisplayName("rechaza incrementos que agotan mas de la categoria sin efecto")
    void rechazaIncrementoExcesivo() {
        List<EfectoEquipamiento> efectos = List.of(
                new EfectoEquipamiento(CategoriaEfecto.CAUSAR_DANO_CRITICO, 31));

        assertThrows(IllegalArgumentException.class,
                () -> DistribucionEfectos.GUERRERO_ARMAS.aplicarEquipamiento(efectos));
    }

    @Test
    @DisplayName("rechaza usar sin efecto como categoria beneficiada")
    void rechazaCategoriaSaldo() {
        assertThrows(IllegalArgumentException.class,
                () -> new EfectoEquipamiento(CategoriaEfecto.SIN_EFECTO, 1));
    }

    @Test
    @DisplayName("rechaza incrementos nulos o negativos")
    void rechazaIncrementosNoPositivos() {
        assertThrows(IllegalArgumentException.class,
                () -> new EfectoEquipamiento(CategoriaEfecto.CAUSAR_DANO, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new EfectoEquipamiento(CategoriaEfecto.CAUSAR_DANO, -1));
    }
}
