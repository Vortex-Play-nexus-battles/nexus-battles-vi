package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.HeroesDeLaMaquina;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.PerfilDeCombate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Heroes de la maquina desde el catalogo de heroes — decision D-B7-11.
 *
 * <p>{@code GET /api/v1/heroes} da los prototipos; se quitan los sanadores (un
 * sanador no inflige dano, §6.1.1: la maquina no podria ganar) y se sortea uno
 * por cupo con {@code SecureRandom}. {@code GET /api/v1/heroes/{p}/niveles/{n}}
 * da su vida y su defensa en el nivel del heroe del anfitrion. Sin
 * equipamiento: la maquina no tiene inventario.
 *
 * <p>Lectura publica del catalogo, sin credencial. Si no responde, devuelve lo
 * que haya podido sortear (o nada) y la maquina combate con una copia del
 * heroe del anfitrion: empezar la partida no depende del catalogo.
 */
public class ClienteCatalogoDeHeroes implements HeroesDeLaMaquina {

    private static final Logger BITACORA = LoggerFactory.getLogger(ClienteCatalogoDeHeroes.class);

    private final RestClient http;
    private final String urlHeroes;
    private final RandomGenerator azar;

    public ClienteCatalogoDeHeroes(RestClient http, String urlHeroes) {
        this(http, urlHeroes, new SecureRandom());
    }

    ClienteCatalogoDeHeroes(RestClient http, String urlHeroes, RandomGenerator azar) {
        this.http = Objects.requireNonNull(http);
        this.urlHeroes = urlHeroes.replaceAll("/+$", "");
        this.azar = Objects.requireNonNull(azar);
    }

    @Override
    public List<HeroeDeCombate> elegir(int cuantos, int nivel) {
        List<HeroeDeCombate> elegidos = new ArrayList<>();
        if (cuantos < 1) {
            return elegidos;
        }
        int enNivel = Math.max(PerfilDeCombate.NIVEL_MINIMO, Math.min(PerfilDeCombate.NIVEL_MAXIMO, nivel));
        try {
            List<ResumenHeroe> catalogo = http.get()
                    .uri(urlHeroes + "/api/v1/heroes")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ResumenHeroe>>() { });
            List<ResumenHeroe> queAtacan = catalogo == null ? List.of()
                    : catalogo.stream().filter(h -> h.nombre() != null && !h.esSanador()).toList();
            if (queAtacan.isEmpty()) {
                return elegidos;
            }
            for (int i = 0; i < cuantos; i++) {
                ResumenHeroe prototipo = queAtacan.get(azar.nextInt(queAtacan.size()));
                elegidos.add(enSuNivel(prototipo.nombre(), enNivel));
            }
        } catch (RestClientException | IllegalArgumentException catalogoNoResponde) {
            BITACORA.warn("El catalogo de heroes no respondio al sortear el heroe de la maquina: {}",
                    catalogoNoResponde.getMessage());
        }
        return elegidos;
    }

    private HeroeDeCombate enSuNivel(String prototipo, int nivel) {
        VistaPorNivel vista = http.get()
                .uri(urlHeroes + "/api/v1/heroes/{prototipo}/niveles/{nivel}", prototipo, nivel)
                .header("Accept", "application/json")
                .retrieve()
                .body(VistaPorNivel.class);
        if (vista == null || vista.estadisticas() == null || vista.estadisticas().vida() == null) {
            throw new IllegalArgumentException("el catalogo no dio la vida de " + prototipo);
        }
        return HeroeDeCombate.aPleno(UUID.randomUUID().toString(), prototipo, prototipo,
                        Math.max(1, vista.estadisticas().vida()), vista.estadisticas().defensa(), null)
                .conPerfil(PerfilDeCombate.delCatalogo(nivel));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResumenHeroe(String nombre, String tipo, boolean esSanador) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VistaPorNivel(Estadisticas estadisticas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Estadisticas(Integer vida, Integer defensa) {
    }
}
