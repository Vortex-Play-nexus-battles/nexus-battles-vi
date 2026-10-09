package nexus.misiones.dominio;

import java.util.Arrays;
import java.util.List;

/**
 * Misiones para las pruebas. «El Templo Olvidado» es el ejemplo del documento
 * (7.8.14) con los mismos datos que la semilla; las demas son minimas y solo
 * tienen lo que cada prueba necesita.
 */
public final class Misiones {

    /** El producto EPICA «Velo de Sombras» del catalogo: UUID v3 de «epica-picaro-veneno-velo-de-sombras». */
    public static final String ID_DE_VELO_DE_SOMBRAS = "9c1ea3fd-2f97-33ae-bb4f-2777cea501a5";

    public static final Epica VELO_DE_SOMBRAS = new Epica("Velo de Sombras",
            "+2 a la defensa para todos los héroes.",
            "El héroe se vuelve intangible durante 1 turno, evitando todo el daño recibido y causando "
                    + "envenenamiento al atacante (+3 de daño por veneno durante 2 turnos).",
            ID_DE_VELO_DE_SOMBRAS);

    /** Un heroe de nivel 1 con las estadisticas de la Tabla 6 (Guerrero Armas). */
    public static final HeroeEnMision HEROE =
            new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-1", 1, 0, 8, 44, 11);

    /**
     * Una epica que el catalogo oficial NO tiene (sin productoId): lo que antes era «Velo de Sombras». Sirve para
     * probar que una epica sin producto queda en la coleccion y se informa como no entregada.
     */
    public static final Epica EPICA_SIN_PRODUCTO = new Epica("Eco de Cenizas",
            "+1 a la defensa para todos los héroes.", "El héroe se vuelve intangible durante 1 turno.", null);

    public static final MasterDeMision SOMBRA_DEL_OLVIDO =
            new MasterDeMision("Sombra del Olvido", "Pícaro Veneno", 0.15, VELO_DE_SOMBRAS);

    private Misiones() {
    }

    public static Mision templo() {
        return new Mision("templo-olvidado", Origen.DOCUMENTO, "El Templo Olvidado", Categoria.HISTORIA,
                "En las profundidades del Bosque Sombrío yace un antiguo templo dedicado a los Dioses Olvidados.",
                null, Dificultad.NORMAL, 12, 8, List.of(),
                "En las profundidades del Bosque Sombrío yace un antiguo templo...", null,
                List.of(
                        new Objetivo("Derrotar al Guardián del Templo (Jefe final).", true,
                                TipoDeObjetivo.DERROTAR_JEFE, null, null),
                        new Objetivo("Explorar las 5 cámaras del templo.", true,
                                TipoDeObjetivo.COMPLETAR_ENCUENTROS, null, null),
                        new Objetivo("Completar la misión sin que la vida del héroe baje del 50%.", false,
                                TipoDeObjetivo.VIDA_MINIMA, 50, null),
                        new Objetivo("Derrotar al Máster si aparece.", false,
                                TipoDeObjetivo.DERROTAR_MASTER, null, null),
                        new Objetivo("Encontrar los 3 fragmentos del Sello Antiguo.", false,
                                TipoDeObjetivo.OBTENER_BOTIN, 3, "Fragmento del Sello Antiguo")),
                List.of(
                        new GrupoDeEnemigos("Sombras Corrompidas", 10, "Enemigos básicos con ataque moderado.",
                                "Guerrero Armas", null, null, List.of()),
                        new GrupoDeEnemigos("Guardianes de Piedra", 5, "Enemigos con alta defensa.",
                                "Guerrero Tanque", null, null, List.of()),
                        new GrupoDeEnemigos("Espectros Ancestrales", 3, "Enemigos con ataques mágicos.",
                                "Mago Fuego", null, null, List.of())),
                new Jefe("El Guardián Eterno", "Guerrero Tanque", 100, null,
                        "Guerrero Tanque con 100 puntos de vida y habilidades potenciadas.", List.of()),
                List.of(SOMBRA_DEL_OLVIDO),
                new RecompensasDeMision(50,
                        List.of(new RecompensasDeMision.ObjetoDeRecompensa("Cofre de Bronce",
                                "contiene ítems comunes", 1, null)),
                        List.of(
                                new RecompensasDeMision.ObjetoPotencial("Fragmento del Sello Antiguo", 0.6, 3,
                                        "60 % cada uno, 3 en total", null),
                                new RecompensasDeMision.ObjetoPotencial("Armadura «Piel del Guardián»", 0.2, 1,
                                        null, null),
                                new RecompensasDeMision.ObjetoPotencial("Arma «Espada del Templo»", 0.15, 1,
                                        null, null)),
                        List.of(),
                        new RecompensasDeMision.PrimeraVez(10, List.of("Título «Explorador del Templo»"))),
                true, null, null);
    }

    public static Mision historia(String id, List<MasterDeMision> masters) {
        return minima(id, Categoria.HISTORIA, 1, List.of(), masters, null);
    }

    public static Mision historiaTrasDe(String id, String... previas) {
        return minima(id, Categoria.HISTORIA, 1, Arrays.asList(previas), List.of(), null);
    }

    public static Mision exploracion(String id, double horas, MasterDeMision... masters) {
        return minima(id, Categoria.EXPLORACION, horas, List.of(), List.of(masters), null);
    }

    public static Mision desafio(String id, Intentos intentos) {
        return minima(id, Categoria.DESAFIO, 2, List.of(), List.of(), intentos);
    }

    /** Una mision minima con los enemigos y el jefe que la prueba necesita (para ver con que estrategia pelea cada uno). */
    public static Mision conEnemigos(String id, List<GrupoDeEnemigos> enemigos, Jefe jefe) {
        return conEnemigosEnNivel(id, null, enemigos, jefe);
    }

    /** Igual, con el nivel recomendado de la mision (D-42: en ese nivel pelean los enemigos). */
    public static Mision conEnemigosEnNivel(String id, Integer nivelRecomendado, List<GrupoDeEnemigos> enemigos,
                                            Jefe jefe) {
        return new Mision(id, Origen.PROVISIONAL_DEV, "Misión " + id, Categoria.HISTORIA, "Descripción de " + id, null,
                Dificultad.FACIL, 1, nivelRecomendado, List.of(), "Narrativa de " + id, null,
                List.of(new Objetivo("Derrotar al jefe.", true, TipoDeObjetivo.DERROTAR_JEFE, null, null)),
                enemigos, jefe, List.of(),
                new RecompensasDeMision(5, List.of(), List.of(), List.of(),
                        new RecompensasDeMision.PrimeraVez(2, List.of())),
                false, null, null);
    }

    /** Una mision minima con los enemigos, el jefe y los Master que la prueba necesita (para ver contra quien pelea). */
    public static Mision conEnemigosYMasters(String id, List<GrupoDeEnemigos> enemigos, Jefe jefe,
                                             List<MasterDeMision> masters) {
        return conEnemigosYMastersEnNivel(id, null, enemigos, jefe, masters);
    }

    /** Igual, con el nivel recomendado de la mision (D-42: en ese nivel pelean los enemigos regulares y el jefe). */
    public static Mision conEnemigosYMastersEnNivel(String id, Integer nivelRecomendado,
                                                    List<GrupoDeEnemigos> enemigos, Jefe jefe,
                                                    List<MasterDeMision> masters) {
        return new Mision(id, Origen.PROVISIONAL_DEV, "Misión " + id, Categoria.HISTORIA, "Descripción de " + id, null,
                Dificultad.FACIL, 1, nivelRecomendado, List.of(), "Narrativa de " + id, null,
                List.of(new Objetivo("Derrotar al jefe.", true, TipoDeObjetivo.DERROTAR_JEFE, null, null)),
                enemigos, jefe, masters,
                new RecompensasDeMision(5, List.of(), List.of(), List.of(),
                        new RecompensasDeMision.PrimeraVez(2, List.of())),
                false, null, null);
    }

    public static Mision minima(String id, Categoria categoria, double horas, List<String> previas,
                                List<MasterDeMision> masters, Intentos intentos) {
        return new Mision(id, Origen.PROVISIONAL_DEV, "Misión " + id, categoria, "Descripción de " + id, null,
                Dificultad.FACIL, horas, null, previas, "Narrativa de " + id, null,
                List.of(new Objetivo("Derrotar al jefe.", true, TipoDeObjetivo.DERROTAR_JEFE, null, null)),
                List.of(new GrupoDeEnemigos("Enemigo de prueba", 1, null, "Guerrero Armas", 4, 5, List.of())),
                new Jefe("Jefe de prueba", "Guerrero Tanque", 6, 5, null, List.of()),
                masters,
                new RecompensasDeMision(5, List.of(), List.of(), List.of(),
                        new RecompensasDeMision.PrimeraVez(2, List.of())),
                false, null, intentos);
    }
}
