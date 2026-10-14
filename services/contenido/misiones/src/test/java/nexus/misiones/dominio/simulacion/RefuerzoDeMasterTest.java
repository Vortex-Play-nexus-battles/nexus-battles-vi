package nexus.misiones.dominio.simulacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nexus.misiones.dominio.Epica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Seccion 7.8.4: un Master tiene «estadisticas superiores a enemigos regulares». Los dos niveles de mas (RG-107) no
 * bastan siempre: el tope 8, un prototipo de menos vida que el de los regulares o unos dados mas chicos dejan al
 * Master por debajo. El refuerzo es el piso que lo garantiza (HU-SIM-006, criterio 1).
 */
class RefuerzoDeMasterTest {

    private static final Epica VELO = new Epica("Velo de Sombras", "+2 a la defensa", "Intangible", null);

    private static Rival regular(String prototipo, int vida, int defensa, Formula ataque, Formula dano) {
        return new Rival("Regular " + prototipo, TipoDeRival.REGULAR, prototipo, 8, vida, defensa, 10, List.of(), null,
                ataque, dano, null);
    }

    private static Rival master(int vida, int defensa, Formula ataque, Formula dano) {
        return new Rival("Sombra del Olvido", TipoDeRival.MASTER, "Pícaro Veneno", 8, vida, defensa, 8,
                List.of(List.of("Flor de loto")), VELO, ataque, dano, null);
    }

    @Test
    @DisplayName("un Master con menos vida y defensa que el regular mas fuerte queda por encima de los dos")
    void subeVidaYDefensa() {
        Rival tanque = regular("Guerrero Tanque", 352, 88, new Formula(80, 1, 6), new Formula(0, 1, 4));
        Rival armas = regular("Guerrero Armas", 300, 70, new Formula(80, 1, 6), new Formula(0, 1, 6));

        Rival reforzado = RefuerzoDeMaster.reforzar(master(288, 64, new Formula(80, 1, 10), new Formula(0, 1, 6)),
                List.of(tanque, armas));

        assertThat(reforzado.vida()).isGreaterThan(tanque.vida());
        assertThat(reforzado.defensa()).isGreaterThan(tanque.defensa());
    }

    @Test
    @DisplayName("el dano y el ataque se comparan por su valor esperado: base mas la media de los dados")
    void danoYAtaqueEsperados() {
        // Mago de fuego regular: ataque 80 + 1d8 (84,5) y dano 1d8 (4,5). El Master trae 1d10 (85,5) y 1d6 (3,5).
        Rival mago = regular("Mago Fuego", 40, 10, new Formula(80, 1, 8), new Formula(0, 1, 8));

        Rival reforzado = RefuerzoDeMaster.reforzar(master(500, 100, new Formula(80, 1, 10), new Formula(0, 1, 6)),
                List.of(mago));

        assertThat(reforzado.ataque().esperado()).isGreaterThan(mago.ataque().esperado());
        assertThat(reforzado.dano().esperado()).isGreaterThan(mago.dano().esperado());
        // Lo que ya era superior no se toca: el ataque trae 85,5 contra 84,5.
        assertThat(reforzado.ataque()).isEqualTo(new Formula(80, 1, 10));
        // Y los dados no cambian: solo se suma a la base lo que falta.
        assertThat(reforzado.dano().cantidadDados()).isEqualTo(1);
        assertThat(reforzado.dano().caras()).isEqualTo(6);
    }

    @Test
    @DisplayName("un Master que ya es superior en todo queda tal cual")
    void yaSuperior() {
        Rival regular = regular("Guerrero Tanque", 44, 11, new Formula(10, 1, 6), new Formula(0, 1, 4));
        Rival fuerte = master(200, 40, new Formula(30, 1, 10), new Formula(5, 1, 6));

        assertThat(RefuerzoDeMaster.reforzar(fuerte, List.of(regular))).isEqualTo(fuerte);
    }

    @Test
    @DisplayName("solo se compara con los regulares de la mision: sin ellos, el Master queda como estaba")
    void sinRegulares() {
        Rival solo = master(10, 2, new Formula(10, 1, 10), new Formula(0, 1, 6));

        assertThat(RefuerzoDeMaster.reforzar(solo, List.of())).isEqualTo(solo);
    }

    @Test
    @DisplayName("un Master sanador no tiene ataque ni dano que comparar: se refuerzan su vida y su defensa")
    void masterSanador() {
        Rival tanque = regular("Guerrero Tanque", 352, 88, new Formula(80, 1, 6), new Formula(0, 1, 4));
        Rival sanador = new Rival("Maestro de la Fuente", TipoDeRival.MASTER, "Chamán", 8, 224, 32, 80, List.of(),
                VELO, null, null, new Formula(48, 1, 6));

        Rival reforzado = RefuerzoDeMaster.reforzar(sanador, List.of(tanque));

        assertThat(reforzado.vida()).isGreaterThan(tanque.vida());
        assertThat(reforzado.defensa()).isGreaterThan(tanque.defensa());
        assertThat(reforzado.ataque()).isNull();
        assertThat(reforzado.dano()).isNull();
        assertThat(reforzado.sanar()).isEqualTo(new Formula(48, 1, 6));
    }

    @Test
    @DisplayName("un regular sin formulas (el motor usa las del catalogo) no entra en la comparacion de ataque y dano")
    void regularSinFormulas() {
        Rival sinFormulas = new Rival("Sombra", TipoDeRival.REGULAR, "Guerrero Tanque", 8, 100, 20, 10, List.of(), null);

        Rival reforzado = RefuerzoDeMaster.reforzar(master(90, 15, new Formula(10, 1, 10), new Formula(0, 1, 6)),
                List.of(sinFormulas));

        assertThat(reforzado.vida()).isGreaterThan(100);
        assertThat(reforzado.defensa()).isGreaterThan(20);
        assertThat(reforzado.ataque()).isEqualTo(new Formula(10, 1, 10));
        assertThat(reforzado.dano()).isEqualTo(new Formula(0, 1, 6));
    }

    @Test
    @DisplayName("el refuerzo conserva todo lo demas del Master: nombre, nivel, epica, estrategia y origen")
    void conservaLoDemas() {
        Rival original = new Rival("Sombra del Olvido", TipoDeRival.MASTER, "Pícaro Veneno", 5, 10, 1, 8,
                List.of(List.of("Flor de loto")), VELO, new Formula(10, 1, 10), new Formula(0, 1, 6), null,
                OrigenDeEstrategia.PREDEFINIDA, "picaro-veneno-n4");

        Rival reforzado = RefuerzoDeMaster.reforzar(original,
                List.of(regular("Guerrero Tanque", 44, 11, new Formula(10, 1, 6), new Formula(0, 1, 4))));

        assertThat(reforzado.nombre()).isEqualTo(original.nombre());
        assertThat(reforzado.tipo()).isEqualTo(TipoDeRival.MASTER);
        assertThat(reforzado.nivel()).isEqualTo(5);
        assertThat(reforzado.poder()).isEqualTo(8);
        assertThat(reforzado.rotaciones()).isEqualTo(original.rotaciones());
        assertThat(reforzado.epica()).isEqualTo(VELO);
        assertThat(reforzado.origenDeEstrategia()).isEqualTo(OrigenDeEstrategia.PREDEFINIDA);
        assertThat(reforzado.estrategiaId()).isEqualTo("picaro-veneno-n4");
    }

    @Test
    @DisplayName("superaATodos dice si el Master esta por encima de cada regular en vida, defensa, ataque y dano")
    void superaATodos() {
        Rival tanque = regular("Guerrero Tanque", 352, 88, new Formula(80, 1, 6), new Formula(0, 1, 4));
        Rival debil = master(288, 64, new Formula(80, 1, 10), new Formula(0, 1, 6));

        assertThat(RefuerzoDeMaster.superaATodos(debil, List.of(tanque))).isFalse();
        assertThat(RefuerzoDeMaster.superaATodos(RefuerzoDeMaster.reforzar(debil, List.of(tanque)), List.of(tanque)))
                .isTrue();
    }
}
