package nexus.combate.api;

import nexus.combate.ClienteHeroes;
import nexus.combate.ClienteHeroesException;
import nexus.combate.DetalleAtaque;
import nexus.combate.EstadisticasHeroeRespuesta;
import nexus.combate.HeroeNoEncontradoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolucion de un ataque de principio a fin — RF-JUE-003, RF-JUE-006.
 *
 * <p>Lo que se prueba es la <b>composicion</b>: que se pida al catalogo, que se
 * tiren los dados de la formula que devuelve, y que el resultado del dominio
 * llegue entero a la respuesta. Las reglas en si —los porcentajes de cada
 * categoria, la forma de la tabla— ya estan probadas en el dominio y no se
 * repiten aqui.
 *
 * <p>Se usa {@code semilla} para que las tiradas sean reproducibles. Sin ella
 * el combate es aleatorio de verdad, que es lo correcto en partida pero
 * inservible en una prueba.
 */
@DisplayName("ResolverAtaque · el ataque se resuelve con datos reales del catalogo")
class ResolverAtaqueTest {

    /** Guerrero Tanque, valores reales verificados contra el servicio de heroes. */
    private static final EstadisticasHeroeRespuesta TANQUE =
            new EstadisticasHeroeRespuesta(11, new DetalleAtaque(10, 1, 6));

    /** Chaman: el catalogo lo publica sin formula de ataque, es sanador. */
    private static final EstadisticasHeroeRespuesta SANADOR =
            new EstadisticasHeroeRespuesta(9, null);

    /** Catalogo de mentira que anota a quien le preguntaron. */
    private static final class CatalogoDeMentira implements ClienteHeroes {

        private final EstadisticasHeroeRespuesta respuesta;
        private final RuntimeException fallo;
        private final List<String> consultados = new ArrayList<>();

        private CatalogoDeMentira(EstadisticasHeroeRespuesta respuesta, RuntimeException fallo) {
            this.respuesta = respuesta;
            this.fallo = fallo;
        }

        static CatalogoDeMentira con(EstadisticasHeroeRespuesta respuesta) {
            return new CatalogoDeMentira(respuesta, null);
        }

        static CatalogoDeMentira queFalla(RuntimeException fallo) {
            return new CatalogoDeMentira(null, fallo);
        }

        @Override
        public EstadisticasHeroeRespuesta obtenerEstadisticas(String nombreHeroe) {
            consultados.add(nombreHeroe);
            if (fallo != null) {
                throw fallo;
            }
            return respuesta;
        }
    }

    private static PeticionDeAtaque.Distribucion tanque() {
        return new PeticionDeAtaque.Distribucion("GUERRERO_TANQUE",
                null, null, null, null, null, null);
    }

    private static PeticionDeAtaque ataque(int defensa, Long semilla) {
        return new PeticionDeAtaque("Guerrero Tanque", defensa, tanque(), null, semilla);
    }

    @Test
    @DisplayName("se pregunta al catalogo por el heroe que ataca, no por otro")
    void preguntaPorElAtacante() {
        CatalogoDeMentira catalogo = CatalogoDeMentira.con(TANQUE);

        new ResolverAtaque(catalogo).ejecutar(ataque(5, 42L));

        assertEquals(List.of("Guerrero Tanque"), catalogo.consultados);
    }

    @Test
    @DisplayName("la tirada sale de la formula del catalogo, no de un numero fijo")
    void laTiradaSaleDeLaFormula() {
        // base 10 + 1d6 => entre 11 y 16, nunca fuera.
        RespuestaDeAtaque respuesta =
                new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(ataque(5, 7L));

        assertTrue(respuesta.ataqueResuelto() >= 11 && respuesta.ataqueResuelto() <= 16,
                "tirada fuera del rango de la formula: " + respuesta.ataqueResuelto());
    }

    @Test
    @DisplayName("un ataque que no supera la defensa es SIN_EFECTO y dano cero, no un error")
    void ataqueFallidoEsResultadoLegitimo() {
        // Defensa altisima: ninguna tirada de 10+1d6 la supera.
        RespuestaDeAtaque respuesta =
                new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(ataque(500, 1L));

        assertAll(
                () -> assertEquals("SIN_EFECTO", respuesta.categoria()),
                () -> assertEquals(0, respuesta.danoAplicado()),
                () -> assertNull(respuesta.indiceTabla(), "no se sorteo fila: no hay que fingir una"),
                // La tirada viaja igual: sin ella, un cero no se distingue de un
                // fallo de integracion.
                () -> assertTrue(respuesta.ataqueResuelto() > 0),
                () -> assertEquals(500, respuesta.defensaObjetivo()));
    }

    @Test
    @DisplayName("un ataque que supera la defensa trae categoria, fila y dano")
    void ataqueConEfecto() {
        RespuestaDeAtaque respuesta =
                new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(ataque(0, 3L));

        assertAll(
                () -> assertNotNull(respuesta.categoria()),
                () -> assertNotNull(respuesta.indiceTabla(), "se sorteo fila"),
                () -> assertTrue(respuesta.indiceTabla() >= 1 && respuesta.indiceTabla() <= 8000),
                () -> assertTrue(respuesta.danoAplicado() >= 0));
    }

    @Test
    @DisplayName("con la misma semilla el resultado se repite: el combate es auditable")
    void conSemillaEsReproducible() {
        ResolverAtaque casoDeUso = new ResolverAtaque(CatalogoDeMentira.con(TANQUE));

        RespuestaDeAtaque primera = casoDeUso.ejecutar(ataque(0, 99L));
        RespuestaDeAtaque segunda = casoDeUso.ejecutar(ataque(0, 99L));

        assertEquals(primera, segunda);
    }

    @Test
    @DisplayName("una accion que protege a un companero no hace nada, aunque el ataque sea alto")
    void protegeAlCompanero() {
        // HU-JUE-008: combate cooperativo, objetivo del mismo equipo, y la
        // accion no permite afectar aliados.
        PeticionDeAtaque.Contexto contexto =
                new PeticionDeAtaque.Contexto(true, "MISMO_EQUIPO", false);
        PeticionDeAtaque peticion = new PeticionDeAtaque(
                "Guerrero Tanque", 0, tanque(), contexto, 5L);

        RespuestaDeAtaque respuesta =
                new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(peticion);

        assertAll(
                () -> assertEquals("SIN_EFECTO", respuesta.categoria()),
                () -> assertEquals(0, respuesta.danoAplicado()));
    }

    @Test
    @DisplayName("un heroe sanador no ataca: se dice, en vez de devolver dano cero")
    void sanadorNoAtaca() {
        // Cero se confundiria con un ataque fallido, y quien lleva el combate
        // necesita saber que ese heroe nunca hara dano con esta accion.
        HeroeSinAtaque error = assertThrows(HeroeSinAtaque.class,
                () -> new ResolverAtaque(CatalogoDeMentira.con(SANADOR)).ejecutar(ataque(5, 1L)));

        assertEquals("Guerrero Tanque", error.heroe());
    }

    @Test
    @DisplayName("si el catalogo no responde, el fallo sube: no se inventan estadisticas")
    void catalogoCaido() {
        assertThrows(ClienteHeroesException.class,
                () -> new ResolverAtaque(CatalogoDeMentira.queFalla(
                        new ClienteHeroesException("apagado"))).ejecutar(ataque(5, 1L)));
    }

    @Test
    @DisplayName("un heroe que no existe sube como tal, no como catalogo caido")
    void heroeInexistente() {
        assertThrows(HeroeNoEncontradoException.class,
                () -> new ResolverAtaque(CatalogoDeMentira.queFalla(
                        new HeroeNoEncontradoException("Arquero del Sur", "no existe")))
                        .ejecutar(ataque(5, 1L)));
    }

    @Test
    @DisplayName("los seis porcentajes explicitos ganan al prototipo: es lo que permite el equipamiento")
    void losCamposGananAlPrototipo() {
        // RF-JUE-004: el equipamiento reparte sobre un prototipo base y el
        // resultado llega campo a campo.
        PeticionDeAtaque.Distribucion aMedida = new PeticionDeAtaque.Distribucion(
                "GUERRERO_TANQUE", 100, 0, 0, 0, 0, 0);
        PeticionDeAtaque peticion = new PeticionDeAtaque(
                "Guerrero Tanque", 0, aMedida, null, 11L);

        RespuestaDeAtaque respuesta =
                new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(peticion);

        // Con 100 % en causarDano, cualquier fila de la tabla cae en esa
        // categoria: el prototipo del nombre habria dado otra cosa.
        assertEquals("CAUSAR_DANO", respuesta.categoria());
    }

    @Test
    @DisplayName("una peticion a medias se rechaza antes de molestar al catalogo")
    void peticionesInvalidas() {
        CatalogoDeMentira catalogo = CatalogoDeMentira.con(TANQUE);
        ResolverAtaque casoDeUso = new ResolverAtaque(catalogo);

        assertAll(
                () -> assertThrows(PeticionInvalida.class, () -> casoDeUso.ejecutar(null)),
                () -> assertThrows(PeticionInvalida.class, () -> casoDeUso.ejecutar(
                        new PeticionDeAtaque(null, 5, tanque(), null, 1L))),
                () -> assertThrows(PeticionInvalida.class, () -> casoDeUso.ejecutar(
                        new PeticionDeAtaque("  ", 5, tanque(), null, 1L))),
                () -> assertThrows(PeticionInvalida.class, () -> casoDeUso.ejecutar(
                        new PeticionDeAtaque("Guerrero Tanque", null, tanque(), null, 1L))),
                () -> assertThrows(PeticionInvalida.class, () -> casoDeUso.ejecutar(
                        new PeticionDeAtaque("Guerrero Tanque", -1, tanque(), null, 1L))),
                () -> assertThrows(PeticionInvalida.class, () -> casoDeUso.ejecutar(
                        new PeticionDeAtaque("Guerrero Tanque", 5, null, null, 1L))));

        assertTrue(catalogo.consultados.isEmpty(), "no se pregunta por un ataque mal formado");
    }

    @Test
    @DisplayName("un prototipo que no existe se rechaza nombrando donde estan los buenos")
    void prototipoDesconocido() {
        PeticionDeAtaque.Distribucion inventado =
                new PeticionDeAtaque.Distribucion("PALADIN_SANTO", null, null, null, null, null, null);

        PeticionInvalida error = assertThrows(PeticionInvalida.class,
                () -> new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(
                        new PeticionDeAtaque("Guerrero Tanque", 5, inventado, null, 1L)));

        assertTrue(error.getMessage().contains("/api/v1/combate/distribuciones"), error.getMessage());
    }

    @Test
    @DisplayName("una distribucion sin prototipo y sin los seis campos se rechaza")
    void distribucionAMedias() {
        PeticionDeAtaque.Distribucion aMedias =
                new PeticionDeAtaque.Distribucion(null, 100, null, null, null, null, null);

        assertThrows(PeticionInvalida.class,
                () -> new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(
                        new PeticionDeAtaque("Guerrero Tanque", 5, aMedias, null, 1L)));
    }

    @Test
    @DisplayName("un contexto a medias se rechaza: o va entero o no va")
    void contextoAMedias() {
        PeticionDeAtaque.Contexto incompleto =
                new PeticionDeAtaque.Contexto(true, null, false);

        assertThrows(PeticionInvalida.class,
                () -> new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(
                        new PeticionDeAtaque("Guerrero Tanque", 5, tanque(), incompleto, 1L)));
    }

    @Test
    @DisplayName("una relacion de objetivo que no existe se rechaza")
    void relacionDesconocida() {
        PeticionDeAtaque.Contexto raro =
                new PeticionDeAtaque.Contexto(true, "ALIADO_TEMPORAL", false);

        assertThrows(PeticionInvalida.class,
                () -> new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(
                        new PeticionDeAtaque("Guerrero Tanque", 5, tanque(), raro, 1L)));
    }

    @Test
    @DisplayName("sin semilla el combate es aleatorio de verdad")
    void sinSemillaEsAleatorio() {
        // No se comprueba que dos tiradas difieran -podrian coincidir por azar-
        // sino que el camino sin semilla funciona y da un resultado valido.
        RespuestaDeAtaque respuesta =
                new ResolverAtaque(CatalogoDeMentira.con(TANQUE)).ejecutar(ataque(0, null));

        assertTrue(respuesta.ataqueResuelto() >= 11 && respuesta.ataqueResuelto() <= 16);
    }
}
