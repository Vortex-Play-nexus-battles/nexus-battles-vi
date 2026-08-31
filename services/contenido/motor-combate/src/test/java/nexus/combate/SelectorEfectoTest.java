package nexus.combate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectorEfectoTest {

    @ParameterizedTest
    @CsvSource({
        "1, CAUSAR_DANO",
        "4800, CAUSAR_DANO",
        "4801, CAUSAR_DANO_CRITICO",
        "5200, CAUSAR_DANO_CRITICO",
        "5201, EVADIR_EL_GOLPE",
        "5440, EVADIR_EL_GOLPE",
        "5441, ESCAPAR_AL_GOLPE",
        "5600, ESCAPAR_AL_GOLPE",
        "5601, SIN_EFECTO",
        "8000, SIN_EFECTO"
    })
    void seleccionaCategoriaSegunRangoDeGuerreroArmas(int indice, CategoriaEfecto esperada) {
        CategoriaEfecto resultado = SelectorEfecto.seleccionar(indice, DistribucionEfectos.GUERRERO_ARMAS);

        assertEquals(esperada, resultado);
    }

    @Test
    void rechazaIndiceMenorQueUno() {
        assertThrows(IllegalArgumentException.class,
            () -> SelectorEfecto.seleccionar(0, DistribucionEfectos.GUERRERO_ARMAS));
    }

    @Test
    void rechazaIndiceMayorQueOchoMil() {
        assertThrows(IllegalArgumentException.class,
            () -> SelectorEfecto.seleccionar(8001, DistribucionEfectos.GUERRERO_ARMAS));
    }
}
