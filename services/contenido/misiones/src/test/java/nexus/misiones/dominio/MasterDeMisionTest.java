package nexus.misiones.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Un Master puede fijar su vida y su defensa, como el jefe. Sin ellas pelea con
 * las de su prototipo en su nivel (dos por encima del heroe, 6.1.2): es lo que
 * hacen todos los Master del juego. Fijarlas existe para la semilla del banco
 * E2E, donde un Master de verdad nunca perderia contra el heroe del kit.
 */
class MasterDeMisionTest {

    private static final Epica EPICA = new Epica("Golpe de defensa", "+1 al ataque", "+4 al daño",
            "81af272d-74fb-3dc1-b6ff-01fdc99a1c1d");

    @Test
    @DisplayName("sin vida ni defensa fijadas, las de su prototipo: es lo que hacen los Master del documento")
    void porOmisionLasDelPrototipo() {
        MasterDeMision master = new MasterDeMision("Sombra del Olvido", "Pícaro Veneno", 0.15, EPICA);

        assertThat(master.vida()).isNull();
        assertThat(master.defensa()).isNull();
    }

    @Test
    @DisplayName("vida y defensa fijadas se conservan, y una defensa de 0 es valida")
    void fijadas() {
        MasterDeMision master = new MasterDeMision("Máster de prueba", "Guerrero Tanque", 1.0, 1, 0, EPICA);

        assertThat(master.vida()).isEqualTo(1);
        assertThat(master.defensa()).isZero();
        assertThat(master.probabilidad()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("una vida menor que 1 o una defensa negativa no se publican")
    void invalidas() {
        assertThatThrownBy(() -> new MasterDeMision("Cero", "Guerrero Tanque", 1.0, 0, 0, EPICA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cero")
                .hasMessageContaining("vida");
        assertThatThrownBy(() -> new MasterDeMision("Negativa", "Guerrero Tanque", 1.0, 1, -1, EPICA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Negativa")
                .hasMessageContaining("defensa");
    }
}
