package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El heroe de la maquina — decision D-B7-11 (§7.6: «un heroe aleatorio
 * controlado por la IA»).
 *
 * <p>Se sortea un prototipo del catalogo que NO sea sanador (un sanador no
 * inflige dano, §6.1.1: la maquina no podria ganar), en el nivel pedido, sin
 * equipo. El azar se inyecta con semilla para que la prueba sea reproducible.
 */
@DisplayName("ClienteCatalogoDeHeroes · el heroe aleatorio de la maquina (D-B7-11)")
class ClienteCatalogoDeHeroesTest {

    private static final String HEROES = "http://heroes:8080";
    private static final String CATALOGO = """
            [ { "nombre": "Chaman", "tipo": "Sanador", "esSanador": true },
              { "nombre": "Mago Hielo", "tipo": "Mago", "esSanador": false },
              { "nombre": "Medico", "tipo": "Sanador", "esSanador": true } ]
            """;

    private MockRestServiceServer servidor;
    private RestClient http;

    @BeforeEach
    void montar() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        http = constructor.build();
    }

    private void esperarVista(String prototipoEnUrl, int nivel, int vida, int defensa) {
        servidor.expect(requestTo(HEROES + "/api/v1/heroes/" + prototipoEnUrl + "/niveles/" + nivel))
                .andRespond(withSuccess("{\"nombre\":\"x\",\"nivel\":" + nivel + ",\"estadisticas\":{\"poder\":8,"
                        + "\"vida\":" + vida + ",\"defensa\":" + defensa + "}}", MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("sortea entre los que atacan: con un solo prototipo sin sanar, siempre sale ese, en el nivel pedido")
    void soloLosQueAtacan() {
        servidor.expect(requestTo(HEROES + "/api/v1/heroes"))
                .andRespond(withSuccess(CATALOGO, MediaType.APPLICATION_JSON));
        esperarVista("Mago%20Hielo", 3, 120, 30);
        esperarVista("Mago%20Hielo", 3, 120, 30);
        ClienteCatalogoDeHeroes cliente = new ClienteCatalogoDeHeroes(http, HEROES + "/", new Random(7));

        List<HeroeDeCombate> heroes = cliente.elegir(2, 3);

        servidor.verify();
        assertAll(
                () -> assertEquals(2, heroes.size()),
                () -> assertTrue(heroes.stream().allMatch(h -> "Mago Hielo".equals(h.prototipo()))),
                () -> assertEquals(120, heroes.get(0).vidaMaxima()),
                () -> assertEquals(120, heroes.get(0).vidaActual(), "a vida completa"),
                () -> assertEquals(30, heroes.get(0).defensa()),
                () -> assertEquals(3, heroes.get(0).nivelDeCombate(), "en el nivel del anfitrion"),
                () -> assertTrue(heroes.get(0).perfil().equipamiento().isEmpty(), "la maquina no tiene inventario"),
                () -> assertNull(heroes.get(0).perfil().estadisticas(),
                        "sin estadisticas propias: el motor usa las del catalogo en ese nivel"),
                () -> assertNotEquals(heroes.get(0).id(), heroes.get(1).id(), "cada cupo es un heroe distinto"));
    }

    @Test
    @DisplayName("el nivel se acota a 1..8, y pedir ninguno no llama a nadie")
    void nivelAcotadoYNinguno() {
        servidor.expect(requestTo(HEROES + "/api/v1/heroes"))
                .andRespond(withSuccess(CATALOGO, MediaType.APPLICATION_JSON));
        esperarVista("Mago%20Hielo", 8, 300, 80);
        ClienteCatalogoDeHeroes cliente = new ClienteCatalogoDeHeroes(http, HEROES, new Random(1));

        assertAll(
                () -> assertTrue(cliente.elegir(0, 3).isEmpty()),
                () -> assertEquals(8, cliente.elegir(1, 12).get(0).nivelDeCombate()));
        servidor.verify();
    }

    @Test
    @DisplayName("si el catalogo no responde no hay heroe: la maquina combatira con la copia del anfitrion")
    void catalogoCaido() {
        servidor.expect(requestTo(HEROES + "/api/v1/heroes")).andRespond(withServerError());
        ClienteCatalogoDeHeroes cliente = new ClienteCatalogoDeHeroes(http, HEROES, new Random(1));

        assertTrue(cliente.elegir(2, 1).isEmpty());
    }

    @Test
    @DisplayName("un catalogo solo de sanadores no da heroe a la maquina, en vez de darle uno que no puede ganar")
    void soloSanadores() {
        servidor.expect(requestTo(HEROES + "/api/v1/heroes")).andRespond(withSuccess(
                "[ { \"nombre\": \"Chaman\", \"tipo\": \"Sanador\", \"esSanador\": true } ]",
                MediaType.APPLICATION_JSON));
        ClienteCatalogoDeHeroes cliente = new ClienteCatalogoDeHeroes(http, HEROES, new Random(1));

        assertTrue(cliente.elegir(1, 1).isEmpty());
    }

    @Test
    @DisplayName("si la vista de un nivel falla a mitad, se devuelve lo sorteado hasta ahi")
    void fallaAMitad() {
        servidor.expect(requestTo(HEROES + "/api/v1/heroes"))
                .andRespond(withSuccess(CATALOGO, MediaType.APPLICATION_JSON));
        esperarVista("Mago%20Hielo", 1, 40, 10);
        servidor.expect(requestTo(HEROES + "/api/v1/heroes/Mago%20Hielo/niveles/1")).andRespond(withSuccess(
                "{\"nombre\":\"Mago Hielo\",\"estadisticas\":null}", MediaType.APPLICATION_JSON));
        ClienteCatalogoDeHeroes cliente = new ClienteCatalogoDeHeroes(http, HEROES, new Random(1));

        List<HeroeDeCombate> heroes = cliente.elegir(3, 1);

        assertEquals(1, heroes.size(), "el primero se sorteo bien; el segundo no tenia vida y se para ahi");
    }

    @Test
    @DisplayName("con varios prototipos que atacan, el sorteo los reparte (200 sorteos con semilla)")
    void elSorteoReparte() {
        String variados = """
                [ { "nombre": "Mago Hielo", "tipo": "Mago", "esSanador": false },
                  { "nombre": "Picaro Veneno", "tipo": "Picaro", "esSanador": false } ]
                """;
        servidor.expect(ExpectedCount.once(), requestTo(HEROES + "/api/v1/heroes"))
                .andRespond(withSuccess(variados, MediaType.APPLICATION_JSON));
        servidor.expect(ExpectedCount.manyTimes(),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                                org.hamcrest.Matchers.containsString("/niveles/1")))
                .andRespond(withSuccess("{\"estadisticas\":{\"vida\":40,\"defensa\":10}}",
                        MediaType.APPLICATION_JSON));
        ClienteCatalogoDeHeroes cliente = new ClienteCatalogoDeHeroes(http, HEROES, new Random(2026));

        List<HeroeDeCombate> heroes = cliente.elegir(200, 1);

        long magos = heroes.stream().filter(h -> "Mago Hielo".equals(h.prototipo())).count();
        assertAll(
                () -> assertEquals(200, heroes.size()),
                // Binomial(200, 1/2): desviacion tipica ~7; ±40 no falla por azar.
                () -> assertTrue(magos > 60 && magos < 140, "magos: " + magos));
    }
}
