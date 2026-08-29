package nexus.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * HU-HER-001 Seleccion de heroe y HU-HER-002 Estadisticas base del heroe.
 * Fuentes de cada regla: Tablas 5 y 6 (heroes.md, seccion 6.1.1), Tabla 7
 * (habilidades-y-mejoras.md) y actas de clase citadas en cada prueba.
 */
class HeroesTest {

    // ---- HU-HER-001 — Seleccion de heroe ----

    @Test
    @DisplayName("el catalogo muestra todos los prototipos definidos en este momento")
    void catalogoMuestraLosPrototiposDefinidos() {
        // Criterio 1: sin cantidad fija (acta 2026-08-13: "no son ocho").
        assertEquals(8, Catalogo.conPrototiposIniciales().listar().size());
    }

    @Test
    @DisplayName("un prototipo nuevo aparece en el catalogo sin cambios del sistema")
    void unPrototipoNuevoApareceSinCambios() {
        Catalogo catalogo = Catalogo.conPrototiposIniciales();
        catalogo.registrar(prototipoDePrueba("Paladín"));
        assertEquals(9, catalogo.listar().size());
    }

    @Test
    @DisplayName("el catalogo rechaza un prototipo con nombre repetido")
    void catalogoRechazaNombresRepetidos() {
        Catalogo catalogo = Catalogo.conPrototiposIniciales();
        // Repetido incluso variando tildes y mayusculas: "chaman" choca con "Chamán".
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> catalogo.registrar(prototipoDePrueba("chaman")));
        assertTrue(error.getMessage().contains("Ya existe"));
        assertEquals(8, catalogo.listar().size());
    }

    @Test
    @DisplayName("la ficha de un prototipo muestra su identidad y sus tres acciones")
    void laFichaMuestraIdentidadYAcciones() {
        // Criterio 2 + regla de las 3 acciones (Tabla 7).
        Prototipo ficha = Catalogo.conPrototiposIniciales().fichaDe("Guerrero Tanque");
        assertEquals("Guerrero Tanque", ficha.nombre());
        assertEquals("Guerrero", ficha.tipo());
        assertEquals(3, ficha.acciones().size());
        for (Accion accion : ficha.acciones()) {
            assertFalse(accion.nombre().isBlank());
            assertFalse(accion.efecto().isBlank());
        }
    }

    /** Descripciones textuales de la Tabla 5, duplicadas aqui a proposito como fixture de fidelidad. */
    private static final Map<String, String> DESCRIPCIONES_TABLA_5 = Map.of(
            "Guerrero Tanque",
            "Este personaje se distingue por su alta resistencia a los ataques enemigos, aunque su capacidad ofensiva es limitada.",
            "Guerrero Armas",
            "Aunque también es un guerrero, su especialidad radica en infligir el máximo daño posible, a expensas de su defensa.",
            "Mago Fuego",
            "Entrenado en la magia elemental, este personaje posee la habilidad de causar un daño considerable al oponente.",
            "Mago Hielo",
            "En contraste con el Mago Fuego, su pericia se centra en debilitar al enemigo para asegurar una victoria estratégica.",
            "Pícaro Veneno",
            "Experto en alquimia, puede infligir un daño significativo al adversario, afectando sus habilidades y disminuyendo su precisión.",
            "Pícaro Machete",
            "Con entrenamiento en armas cortantes, es un especialista en la provocación de heridas letales.",
            "Chamán",
            "Este es un sanador que ha dedicado su estudio a la naturaleza y ha comprendido su interrelación con los seres racionales. Posee la habilidad de sanar a sus congéneres. Sin embargo, carece de capacidad ofensiva, ya que le está vedado infligir daño a cualquier otro ser vivo.",
            "Médico",
            "Es un especialista en ciencias de la salud, cuya función principal es la recuperación y el apoyo a sus aliados. A diferencia del Chamán, cuya especialización se centra en la naturaleza y la curación pasiva, el Médico aplica conocimientos científicos y habilidades prácticas para la restauración activa y eficiente de la salud de sus compañeros.");

    @ParameterizedTest
    @ValueSource(strings = {
            "Guerrero Tanque", "Guerrero Armas", "Mago Fuego", "Mago Hielo",
            "Pícaro Veneno", "Pícaro Machete", "Chamán", "Médico"})
    @DisplayName("los prototipos de hoy coinciden con la Tabla 5 en nombre y descripcion")
    void losPrototiposCoincidenConTabla5(String nombre) {
        // Criterio 3: coincidencia exacta de nombre Y descripcion.
        Prototipo ficha = Catalogo.conPrototiposIniciales().fichaDe(nombre);
        assertEquals(nombre, ficha.nombre());
        assertEquals(DESCRIPCIONES_TABLA_5.get(nombre), ficha.descripcion());
    }

    @Test
    @DisplayName("consultar un prototipo inexistente produce un error con mensaje claro")
    void prototipoInexistenteProduceMensajeClaro() {
        // Regla del cliente, acta 2026-08-13: "uno como usuario jamas deberia ver un status de HTML".
        Catalogo catalogo = Catalogo.conPrototiposIniciales();
        HeroeNoDisponibleException error =
                assertThrows(HeroeNoDisponibleException.class, () -> catalogo.fichaDe("Nigromante"));
        assertTrue(error.getMessage().contains("no está disponible"));
        assertFalse(error.getMessage().matches(".*(404|500|[Hh][Tt][Tt][Pp]).*"));
    }

    // ---- HU-HER-002 — Estadisticas base del heroe ----

    @ParameterizedTest
    @CsvSource(nullValues = "NULO", value = {
            "Guerrero Tanque, 10, 44, 11, 10 + 1d6,  1d4,  NULO",
            "Guerrero Armas,   8, 44, 11, 10 + 1d6,  1d6,  NULO",
            "Mago Fuego,       8, 40, 10, 10 + 1d8,  1d8,  NULO",
            "Mago Hielo,      10, 40, 10, 10 + 1d8,  1d6,  NULO",
            "Pícaro Veneno,    8, 36,  8, 10 + 1d10, 1d6,  NULO",
            "Pícaro Machete,   8, 36,  8, 10 + 1d10, 1d8,  NULO",
            "Chamán,          10, 28,  4, NULO,      NULO, 6 + 1d6",
            "Médico,          10, 28,  4, NULO,      NULO, 4 + 1d8"})
    @DisplayName("las estadisticas de nivel 1 coinciden con la Tabla 6")
    void estadisticasNivel1CoincidenConTabla6(
            String nombre, int poder, int vida, int defensa, String ataque, String dano, String sanar) {
        Estadisticas stats = Catalogo.conPrototiposIniciales().fichaDe(nombre).estadisticasNivel1();
        assertEquals(poder, stats.poder());
        assertEquals(vida, stats.vida());
        assertEquals(defensa, stats.defensa());
        assertEquals(ataque, stats.ataque() == null ? null : stats.ataque().texto());
        assertEquals(dano, stats.dano() == null ? null : stats.dano().texto());
        assertEquals(sanar, stats.sanar() == null ? null : stats.sanar().texto());
    }

    @Test
    @DisplayName("las formulas se guardan estructuradas, no como texto a parsear")
    void formulasEstructuradas() {
        // El motor de combate consumira base/cantidad/caras sin parsear strings.
        Formula ataqueMagoFuego = Catalogo.conPrototiposIniciales()
                .fichaDe("Mago Fuego").estadisticasNivel1().ataque();
        assertEquals(10, ataqueMagoFuego.base());
        assertEquals(1, ataqueMagoFuego.cantidadDados());
        assertEquals(8, ataqueMagoFuego.carasDado());
        assertEquals("10 + 1d8", ataqueMagoFuego.texto());
        assertEquals("1d4", new Formula(0, 1, 4).texto());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Chamán", "Médico"})
    @DisplayName("los sanadores no poseen estadisticas ofensivas y usan Sanar")
    void sanadoresSinOfensivas(String nombre) {
        // Seccion 6.1.1, p. 25: unicos dedicados a la sanacion, sin capacidad ofensiva.
        Prototipo ficha = Catalogo.conPrototiposIniciales().fichaDe(nombre);
        assertTrue(ficha.esSanador());
        assertNull(ficha.estadisticasNivel1().ataque());
        assertNull(ficha.estadisticasNivel1().dano());
        assertNotNull(ficha.estadisticasNivel1().sanar());
    }

    @Test
    @DisplayName("todo prototipo no sanador usa Ataque y Daño y no posee Sanar")
    void noSanadoresConOfensivas() {
        // Redaccion abierta: el numero de heroes crecera (acta 2026-08-13).
        List<Prototipo> noSanadores = Catalogo.conPrototiposIniciales().listar().stream()
                .filter(p -> !p.esSanador()).toList();
        assertFalse(noSanadores.isEmpty());
        for (Prototipo p : noSanadores) {
            assertNotNull(p.estadisticasNivel1().ataque());
            assertNotNull(p.estadisticasNivel1().dano());
            assertNull(p.estadisticasNivel1().sanar());
        }
    }

    @Test
    @DisplayName("un heroe recien creado nace en nivel 1 con las estadisticas de su prototipo")
    void heroeRecienCreadoNaceEnNivel1() {
        // Seccion 6.1.1, p. 26: "El nivel inicial de todos los personajes es uno (1)".
        for (Prototipo prototipo : PrototiposIniciales.LISTA) {
            Heroe heroe = Heroe.crear(prototipo);
            assertEquals(1, heroe.nivel());
            assertEquals(prototipo.estadisticasNivel1(), heroe.estadisticas());
        }
    }

    private static Prototipo prototipoDePrueba(String nombre) {
        return new Prototipo(
                nombre, "Guerrero", "Prototipo de prueba agregado por un administrador.",
                false,
                new Estadisticas(9, 42, 10, new Formula(10, 1, 6), new Formula(0, 1, 6), null),
                List.of(
                        new Accion("Juicio", 2, "+1 al ataque"),
                        new Accion("Escudo sagrado", 4, "+8 a la defensa"),
                        new Accion("Castigo", 6, "+2 al daño")));
    }
}
