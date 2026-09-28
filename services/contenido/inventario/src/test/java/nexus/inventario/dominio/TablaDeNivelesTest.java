package nexus.inventario.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Seccion 6.1.1: experiencia para subir 100 x 1,2^(n-1), el sobrante se conserva y el 8 es tope. */
class TablaDeNivelesTest {

    /** La que publica heroes: niveles 1 a 7 con techo, el 8 sin el. */
    static TablaDeNiveles delDocumento() {
        List<Double> paraSubir = new ArrayList<>();
        for (int nivel = 1; nivel <= 7; nivel++) {
            paraSubir.add(100 * Math.pow(1.2, nivel - 1));
        }
        return new TablaDeNiveles(paraSubir);
    }

    @Test
    @DisplayName("sin llegar al techo se acumula en el nivel")
    void acumula() {
        TablaDeNiveles.Progresion p = delDocumento().sumar(1, 10, 50);

        assertThat(p.nivel()).isEqualTo(1);
        assertThat(p.experiencia()).isEqualTo(60);
    }

    @Test
    @DisplayName("sube encadenando niveles y conserva el sobrante")
    void subeConSobrante() {
        // Del 1 al 3 hacen falta 100 + 120 = 220; con 250 sobran 30.
        TablaDeNiveles.Progresion p = delDocumento().sumar(1, 0, 250);

        assertThat(p.nivel()).isEqualTo(3);
        assertThat(p.experiencia()).isEqualTo(30, within(1e-9));
    }

    @Test
    @DisplayName("en el nivel 8 la experiencia se acumula sin subir")
    void topeOcho() {
        TablaDeNiveles tabla = delDocumento();

        assertThat(tabla.nivelMaximo()).isEqualTo(8);
        TablaDeNiveles.Progresion p = tabla.sumar(7, 0, 10_000);
        assertThat(p.nivel()).isEqualTo(8);
        assertThat(p.experiencia()).isEqualTo(10_000 - 100 * Math.pow(1.2, 6), within(1e-9));
        assertThat(tabla.sumar(8, 5, 5).nivel()).isEqualTo(8);
    }

    @Test
    @DisplayName("una tabla vacia o con techos no positivos no se acepta; tampoco experiencia negativa")
    void validaciones() {
        assertThatThrownBy(() -> new TablaDeNiveles(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TablaDeNiveles(List.of(100.0, 0.0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> delDocumento().sumar(1, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
