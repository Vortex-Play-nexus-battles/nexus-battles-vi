package nexus.misiones.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.EventosDePrueba;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * El documento de {@code eventos_de_combate} tal como lo escribe y lo lee el conversor de Spring Data, sin
 * MongoDB: es lo que cuentan las IT de Testcontainers (que aqui, sin Docker, no corren) en cuanto al formato.
 * Lo consume el entrenamiento de HU-SIM-008, asi que los nombres de campo son contrato.
 *
 * <p>Los eventos sinteticos de {@code ia/pruebas/eventos-sinteticos.jsonl} (los genera Python, en el formato
 * que se supone es el de misiones) se leen con el conversor real: si el formato de Python se desvia del de
 * misiones, aqui se nota.
 */
class EventoDeCombateMapeoTest {

    private static final UUID EJECUCION = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");
    private static final Path SINTETICOS = Path.of("ia", "pruebas", "eventos-sinteticos.jsonl");

    /**
     * Rutas que el evento de prueba completo escribe y los sinteticos no tienen por construccion: un duelo
     * sintetico no tiene turnos rechazados por el motor ni efectos vigentes. El estado de cada combatiente
     * (antes y despues, actor y oponente) tiene la misma forma: se compara como «estado».
     */
    private static final Set<String> RUTAS_QUE_LOS_SINTETICOS_NO_USAN = Set.of(
            "estado.efectos.nombre", "estado.efectos.tipo", "estado.efectos.turnos", "estado.efectos.valor",
            "jugada.rechazadas.accion", "jugada.rechazadas.motivo",
            // HU-SIM-004: el generador sintetico no modela la estrategia de los enemigos (el entrenamiento no la lee).
            "jugada.estrategia", "jugada.estrategiaId");

    private MappingMongoConverter conversor;

    @BeforeEach
    void armar() {
        // Las mismas conversiones que registra Spring Boot (Instant <-> Date, etc.).
        MongoCustomConversions conversiones = new MongoCustomConversions(List.of());
        MongoMappingContext contexto = new MongoMappingContext();
        contexto.setAutoIndexCreation(false);
        contexto.setSimpleTypeHolder(conversiones.getSimpleTypeHolder());
        contexto.afterPropertiesSet();
        conversor = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, contexto);
        conversor.setCustomConversions(conversiones);
        conversor.afterPropertiesSet();
    }

    private Document escribir(EventoDeCombateDocumento documento) {
        Document bson = new Document();
        conversor.write(documento, bson);
        return bson;
    }

    @Test
    @DisplayName("el evento completo se escribe con los nombres de campo del contrato y vuelve identico")
    void idaYVueltaPorElConversor() {
        EventoDeCombate evento = EventosDePrueba.evento(EJECUCION, 7);

        Document bson = escribir(EventoDeCombateDocumento.de(evento, Instant.parse("2026-10-01T10:00:00Z")));

        assertThat(bson.getString("_id")).isEqualTo(EJECUCION + ":7");
        assertThat(((Document) bson.get("oponente")).getString("prototipo")).isEqualTo("Guerrero Tanque");
        Document jugada = (Document) bson.get("jugada");
        assertThat(jugada.getString("decididaPor")).isEqualTo("MODELO");
        assertThat(jugada.getString("versionDelModelo")).isEqualTo("v1-prueba");
        List<?> candidatas = (List<?>) jugada.get("candidatas");
        assertThat(candidatas).hasSize(2);
        Document primera = (Document) candidatas.get(0);
        assertThat(primera.getString("accion")).isEqualTo("Embate sangriento");
        assertThat(primera.getInteger("rotacion")).isEqualTo(1);
        assertThat(primera.getDouble("puntaje")).isEqualTo(0.75);
        // Lo nulo no se escribe (el ataque basico no tiene rotacion).
        assertThat(((Document) candidatas.get(1)).containsKey("rotacion")).isFalse();

        EventoDeCombateDocumento leido = conversor.read(EventoDeCombateDocumento.class, bson);
        assertThat(leido.aDominio()).isEqualTo(evento);
    }

    @Test
    @DisplayName("un evento de la regla sola vuelve como REGLA, sin version de modelo ni candidatas")
    void laReglaSola() {
        EventoDeCombate completo = EventosDePrueba.evento(EJECUCION, 1);
        EventoDeCombate.Jugada de = completo.jugada();
        EventoDeCombate.Jugada soloRegla = new EventoDeCombate.Jugada(de.decidida(), de.ejecutada(), false,
                de.costoDecidido(), de.costoDePoder(), de.rechazadas(), de.resultado());
        EventoDeCombate evento = new EventoDeCombate(EJECUCION, completo.misionId(), 1, completo.encuentro(),
                completo.enemigo(), completo.turno(), completo.actor(), completo.oponente(), completo.antes(),
                completo.alIniciar(), soloRegla, completo.despues());

        Document jugada = (Document) escribir(EventoDeCombateDocumento.de(evento, Instant.now())).get("jugada");

        assertThat(jugada.getString("decididaPor")).isEqualTo("REGLA");
        assertThat(jugada.containsKey("versionDelModelo")).isFalse();
        assertThat(((List<?>) jugada.get("candidatas"))).isEmpty();
    }

    @Test
    @DisplayName("la estrategia que jugo un enemigo (HU-SIM-004) se escribe como estrategia y estrategiaId y vuelve identica")
    void laEstrategiaDelEnemigo() {
        EventoDeCombate evento = EventosDePrueba.eventoDeEnemigo(EJECUCION, 4);

        Document jugada = (Document) escribir(EventoDeCombateDocumento.de(evento, Instant.now())).get("jugada");

        assertThat(jugada.getString("estrategia")).isEqualTo("PREDEFINIDA");
        assertThat(jugada.getString("estrategiaId")).isEqualTo("mago-fuego-n4");
        EventoDeCombateDocumento leido = conversor.read(EventoDeCombateDocumento.class,
                escribir(EventoDeCombateDocumento.de(evento, Instant.now())));
        assertThat(leido.aDominio()).isEqualTo(evento);
    }

    @Test
    @DisplayName("la jugada de quien no tiene estrategia (el heroe, o un enemigo con ataque basico siempre) no escribe esos campos")
    void sinEstrategiaNoSeEscribe() {
        Document jugada = (Document) escribir(EventoDeCombateDocumento.de(EventosDePrueba.evento(EJECUCION, 1),
                Instant.now())).get("jugada");

        assertThat(jugada.containsKey("estrategia")).isFalse();
        assertThat(jugada.containsKey("estrategiaId")).isFalse();
    }

    @Test
    @DisplayName("los eventos sinteticos de Python se leen con el conversor real y se escriben igual: mismo formato")
    void sinteticosConElFormatoDeMisiones() throws IOException {
        List<String> lineas = Files.readAllLines(SINTETICOS, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank()).toList();
        assertThat(lineas).hasSizeGreaterThan(50);

        Set<String> rutasDeLosSinteticos = new TreeSet<>();
        for (String linea : lineas) {
            Document original = Document.parse(linea);
            Document escrito = escribir(conversor.read(EventoDeCombateDocumento.class, original));
            // Mongo agrega _class al raiz; el resto debe ser lo mismo que escribio Python.
            escrito.remove("_class");
            assertThat(sinNulos(escrito)).isEqualTo(sinNulos(original));
            rutas("", original, rutasDeLosSinteticos);
        }

        Set<String> rutasDeMisiones = new TreeSet<>();
        rutas("", escribir(EventoDeCombateDocumento.de(EventosDePrueba.evento(EJECUCION, 1), Instant.now())),
                rutasDeMisiones);
        // La estrategia solo la trae la jugada de un enemigo: el documento tiene que escribirla.
        rutas("", escribir(EventoDeCombateDocumento.de(EventosDePrueba.eventoDeEnemigo(EJECUCION, 2), Instant.now())),
                rutasDeMisiones);
        rutasDeMisiones.remove("_class");

        Set<String> sinteticas = comoEstado(rutasDeLosSinteticos);
        Set<String> deMisiones = comoEstado(rutasDeMisiones);
        // Python no inventa campos que misiones no escribe...
        assertThat(sinteticas).isSubsetOf(deMisiones);
        // ...ni deja de escribir los que misiones si escribe (salvo lo que un duelo sintetico no produce).
        Set<String> faltantes = new TreeSet<>(deMisiones);
        faltantes.removeAll(sinteticas);
        faltantes.removeAll(RUTAS_QUE_LOS_SINTETICOS_NO_USAN);
        assertThat(faltantes).as("campos del documento que el generador sintetico no escribe").isEmpty();
    }

    // ----------------------------------------------------------------- utilidades

    private static Set<String> comoEstado(Set<String> rutas) {
        Set<String> normalizadas = new TreeSet<>();
        rutas.forEach(r -> normalizadas.add(r.replaceFirst("^(antes|despues)[.](actor|oponente)", "estado")));
        return normalizadas;
    }

    /** Todos los caminos de campo (sin indices de lista) que aparecen en el documento. */
    private static void rutas(String prefijo, Object valor, Set<String> destino) {
        if (valor instanceof Map<?, ?> mapa) {
            mapa.forEach((k, v) -> {
                String ruta = prefijo.isEmpty() ? String.valueOf(k) : prefijo + "." + k;
                if (v instanceof Map<?, ?> || v instanceof List<?>) {
                    rutas(ruta, v, destino);
                } else {
                    destino.add(ruta);
                }
            });
        } else if (valor instanceof List<?> lista) {
            lista.forEach(v -> rutas(prefijo, v, destino));
        }
    }

    /** El documento sin campos nulos, para comparar lo que Python omite con lo que Spring Data omite. */
    private static Object sinNulos(Object valor) {
        if (valor instanceof Map<?, ?> mapa) {
            Document limpio = new Document();
            mapa.forEach((k, v) -> {
                if (v != null) {
                    limpio.put(String.valueOf(k), sinNulos(v));
                }
            });
            return limpio;
        }
        if (valor instanceof List<?> lista) {
            return lista.stream().map(EventoDeCombateMapeoTest::sinNulos).toList();
        }
        if (valor instanceof Number numero && !(valor instanceof Double) && !(valor instanceof Float)) {
            return numero.longValue();
        }
        return valor;
    }
}
