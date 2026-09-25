package nexus.inventario.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 1.6.0 (B9), secciones 7.8.6 y 7.8.10: el heroe en mision «queda bloqueado y
 * no puede usarse en otros modos», «no puede ser modificado su equipamiento» y
 * vuelve con su experiencia.
 */
class HeroeEnMisionTest {

    private static final String EJECUCION = "0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11";
    private static final String OTRA = "5e2b8c1a-7d3f-4c11-8a2e-9b0c4d5e6f70";

    private static Inventario inventario() {
        return new Inventario("inventario-1", "jugador-A", List.of(
                new ElementoInventario("heroe-1", "producto-heroe", TipoElementoInventario.HEROE, "Vorn"),
                new ElementoInventario("espada-1", "producto-espada", TipoElementoInventario.ARMA, "Espada"),
                new ElementoInventario("pocion-1", "producto-pocion", TipoElementoInventario.ITEM, "Pocion")));
    }

    @Test
    @DisplayName("bloquear lo deja no disponible e idempotente para la misma ejecucion")
    void bloquear() {
        Inventario enMision = inventario().bloquearEnMision("heroe-1", EJECUCION);

        ElementoInventario heroe = enMision.elemento("heroe-1");
        assertThat(heroe.enMision()).isTrue();
        assertThat(heroe.disponible()).isFalse();
        assertThat(heroe.ejecucionMisionId()).isEqualTo(EJECUCION);
        assertThat(enMision.bloquearEnMision("heroe-1", EJECUCION)).isEqualTo(enMision);
    }

    @Test
    @DisplayName("otra mision, una subasta vigente o algo que no es un heroe no se bloquean")
    void rechazos() {
        Inventario enMision = inventario().bloquearEnMision("heroe-1", EJECUCION);
        assertThatThrownBy(() -> enMision.bloquearEnMision("heroe-1", OTRA))
                .isInstanceOf(HeroeEnMisionException.class);

        Inventario enSubasta = inventario().bloquearEnSubasta("heroe-1", "subasta-1");
        assertThatThrownBy(() -> enSubasta.bloquearEnMision("heroe-1", EJECUCION))
                .isInstanceOf(ElementoNoDisponibleException.class)
                .hasMessageContaining("subasta");

        assertThatThrownBy(() -> inventario().bloquearEnMision("espada-1", EJECUCION))
                .isInstanceOf(NoEsUnHeroeException.class);
    }

    @Test
    @DisplayName("en mision no se equipa, desequipa, renombra, borra ni subasta")
    void nadaMientrasEstaFuera() {
        Inventario equipado = inventario().equipar("heroe-1", "espada-1");
        Inventario enMision = equipado.bloquearEnMision("heroe-1", EJECUCION);

        assertThatThrownBy(() -> enMision.equipar("heroe-1", "pocion-1")).isInstanceOf(HeroeEnMisionException.class);
        assertThatThrownBy(() -> enMision.desequipar("heroe-1", "espada-1"))
                .isInstanceOf(HeroeEnMisionException.class);
        assertThatThrownBy(() -> enMision.renombrarElemento("heroe-1", "Otro"))
                .isInstanceOf(HeroeEnMisionException.class);
        assertThatThrownBy(() -> enMision.eliminarElemento("heroe-1")).isInstanceOf(HeroeEnMisionException.class);
        assertThatThrownBy(() -> enMision.bloquearEnSubasta("heroe-1", "subasta-1"))
                .isInstanceOf(HeroeEnMisionException.class);
        assertThat(enMision.equipamiento("heroe-1").armas()).containsExactly("espada-1");
    }

    @Test
    @DisplayName("liberar quita el bloqueo con la nueva progresion; repetirla no cambia nada")
    void liberar() {
        Inventario enMision = inventario().bloquearEnMision("heroe-1", EJECUCION);

        Inventario liberado = enMision.liberarDeMision("heroe-1", EJECUCION, 2, 15.5);
        ElementoInventario heroe = liberado.elemento("heroe-1");
        assertThat(heroe.disponible()).isTrue();
        assertThat(heroe.nivel()).isEqualTo(2);
        assertThat(heroe.experiencia()).isEqualTo(15.5);
        assertThat(liberado.liberarDeMision("heroe-1", EJECUCION, 3, 99)).isEqualTo(liberado);
        assertThatThrownBy(() -> enMision.liberarDeMision("heroe-1", OTRA, 2, 0))
                .isInstanceOf(HeroeEnMisionException.class);
        // Una subasta que termina no toca el bloqueo de una mision.
        assertThat(enMision.liberarBloqueoSubasta("heroe-1", "subasta-1")).isEqualTo(enMision);
    }

    @Test
    @DisplayName("solo un heroe lleva nivel, experiencia o mision, y en sus limites")
    void invariantes() {
        assertThatThrownBy(() -> new ElementoInventario("x", "p", TipoElementoInventario.ARMA, "Arma", null, null,
                2, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ElementoInventario("x", "p", TipoElementoInventario.HEROE, "H", null, null,
                9, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ElementoInventario("x", "p", TipoElementoInventario.HEROE, "H", null, null,
                1, -1.0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ElementoInventario("x", "p", TipoElementoInventario.HEROE, "H", null, null,
                1, 0.0, " ")).isInstanceOf(IllegalArgumentException.class);
        ElementoInventario nuevo = new ElementoInventario("x", "p", TipoElementoInventario.HEROE, "H");
        assertThat(nuevo.nivelActual()).isEqualTo(1);
        assertThat(nuevo.experienciaActual()).isZero();
    }
}
