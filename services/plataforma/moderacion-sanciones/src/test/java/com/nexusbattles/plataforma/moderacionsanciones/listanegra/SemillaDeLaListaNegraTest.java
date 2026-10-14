package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * La semilla provisional de la lista negra (V7, V9 y V12), leida de las
 * migraciones tal cual y comprobada sin base de datos: que cada forma
 * guardada es la que calcula el normalizador, que no se repite ninguna, que
 * los cortos van como palabra y —lo que mas importa al ampliarla (SA-4,
 * punto 21 del informe)— que ninguna frase corriente del juego, del chat o de
 * un comentario coincide con algun termino. La lista se aplica tambien al
 * chat, que bloquea el mensaje: un falso positivo es un mensaje que no llega.
 */
@DisplayName("Semilla de la lista negra (V7, V9, V12)")
class SemillaDeLaListaNegraTest {

    private static final List<String> MIGRACIONES = List.of(
            "/db/migration/V7__semilla_lista_negra_provisional.sql",
            "/db/migration/V9__semilla_provisional_insultos_comunes.sql",
            "/db/migration/V12__semilla_provisional_ampliada.sql");

    /** («termino», «normalizado», «CATEGORIA», «MODO», ... */
    private static final Pattern FILA = Pattern.compile(
            "\\(\\s*'([^']*)',\\s*'([^']*)',\\s*'([A-Z]+)',\\s*'([A-Z]+)',\\s*true,\\s*'semilla'");

    private record Fila(String migracion, String termino, String normalizado, String categoria, String modo) {
    }

    private static List<Fila> filas() {
        List<Fila> filas = new ArrayList<>();
        for (String migracion : MIGRACIONES) {
            try (InputStream sql = SemillaDeLaListaNegraTest.class.getResourceAsStream(migracion)) {
                assertThat(sql).as(migracion).isNotNull();
                Matcher fila = FILA.matcher(new String(sql.readAllBytes(), StandardCharsets.UTF_8));
                while (fila.find()) {
                    filas.add(new Fila(migracion, fila.group(1), fila.group(2), fila.group(3), fila.group(4)));
                }
            } catch (IOException e) {
                throw new IllegalStateException(migracion, e);
            }
        }
        return filas;
    }

    private static List<TerminoActivo> terminos() {
        return filas().stream()
                .map(f -> new TerminoActivo(f.termino(), f.normalizado(), CategoriaDeTermino.valueOf(f.categoria()),
                        ModoDeCoincidencia.valueOf(f.modo())))
                .toList();
    }

    private static List<String> coincidencias(String texto) {
        return DetectorDeTerminos.coincidencias(NormalizadorDeTexto.normalizar(texto), terminos()).stream()
                .map(TerminoActivo::termino).toList();
    }

    @Test
    @DisplayName("cada fila guarda la forma que calcula el normalizador, sin repetir, con categoria y modo validos")
    void coherente() {
        List<Fila> filas = filas();
        assertThat(filas).as("V7 + V9 + V12").hasSizeGreaterThanOrEqualTo(260);
        Set<String> formas = new HashSet<>();
        for (Fila fila : filas) {
            assertThat(fila.normalizado()).as(fila.termino() + " en " + fila.migracion())
                    .isEqualTo(NormalizadorDeTexto.compacta(fila.termino()));
            assertThat(formas.add(fila.normalizado())).as("forma repetida: " + fila.normalizado()).isTrue();
            assertThat(EnumSet.allOf(CategoriaDeTermino.class).stream().map(Enum::name))
                    .contains(fila.categoria());
            assertThat(EnumSet.allOf(ModoDeCoincidencia.class).stream().map(Enum::name)).contains(fila.modo());
            if (fila.normalizado().length() < ModoDeCoincidencia.LONGITUD_MINIMA_SUBCADENA) {
                assertThat(fila.modo()).as("los cortos van como palabra: " + fila.termino()).isEqualTo("PALABRA");
            }
        }
        Set<String> categorias = filas.stream().map(Fila::categoria).collect(Collectors.toSet());
        assertThat(categorias).as("cada categoria del 7.1.1 tiene ejemplos")
                .contains("OFENSIVO", "MARCA", "CELEBRIDAD", "POLITICO", "DIRIGENTE");
    }

    /**
     * Frases corrientes, de las que salen en una partida, en el chat o en un
     * comentario, elegidas para tocar los bordes: palabras que contienen un
     * termino corto, y dos palabras que al juntarse forman uno largo.
     */
    @ParameterizedTest(name = "«{0}» pasa")
    @ValueSource(strings = {
            "el valor antes de la subasta era mayor", "le di con un tiro fijo", "la llegada final del torneo",
            "me fue mal en Paris", "la camara donada al equipo", "el rubí usado en la espada",
            "a ver gas que se acaba el turno", "superrápido el combate", "llamé cultura a eso",
            "lo tengo mal cogido", "el petróleo sube", "la fruta está madura", "el mes siguiente",
            "esta línea de defensa", "está lindo el escudo", "computadora nueva", "una disputa justa",
            "el vehículo del torneo", "el cálculo del daño", "añadidas al inventario", "el duque de la torre",
            "los santos del templo", "Mi mamá vergüenza no tiene", "el trabajo putrefacto", "tiro con arco",
            "Valkiria", "ElGuerrero", "Mariposa", "Clasico99", "Spidey", "Amazonas", "Llego tarde",
            "juego en la consola", "gané la partida", "Me gusta el juego", "buen combate, crack",
            "la poción de vida", "el héroe recibió daño crítico", "quiero comprar el arma épica",
            "felicidades por la victoria", "hay que cubrir la retaguardia", "su pericia es enorme",
            "el clima de la arena", "nuestra estrategia fue buena", "reconstruir la base",
            "la escopeta del pícaro", "no te rindas", "el mago de fuego", "el guerrero tanque"})
    void frasesCorrientes(String frase) {
        assertThat(coincidencias(frase)).as(frase).isEmpty();
    }

    @ParameterizedTest(name = "«{0}» coincide")
    @ValueSource(strings = {"xXPikachuXx", "malparida", "Hijo De Perra", "Messi10", "Karol G", "gaddafi",
            "pelotudos", "chingadera", "motherfuckers", "SuperMario64", "Pablo_Escobar", "elonmusk",
            "conchatumadre", "Bad Bunny", "putones", "lameculos", "tirofijo", "Saddam Hussein"})
    void evidentes(String texto) {
        assertThat(coincidencias(texto)).as(texto).isNotEmpty();
    }
}
