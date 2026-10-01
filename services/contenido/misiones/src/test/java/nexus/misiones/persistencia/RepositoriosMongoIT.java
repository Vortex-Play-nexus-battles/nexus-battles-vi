package nexus.misiones.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.Ejecucion;
import nexus.misiones.dominio.EjecucionModificadaConcurrentemente;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.EstadoDePaso;
import nexus.misiones.dominio.EstadoEjecucion;
import nexus.misiones.dominio.EstrategiaGuardada;
import nexus.misiones.dominio.Misiones;
import nexus.misiones.dominio.PasoDeLiquidacion;
import nexus.misiones.dominio.RecompensasDeEjecucion;
import nexus.misiones.dominio.simulacion.ResultadoDeMision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * La persistencia contra un MongoDB 8 de verdad: la version optimista, el unico
 * parcial que impide dos ejecuciones en curso de la misma mision, la clave de
 * idempotencia de la matricula y las consultas que hacen de cola del trabajo.
 */
@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
@Testcontainers
@Import({RepositorioEjecucionesMongo.class, RepositorioEstrategiasMongo.class, RepositorioFavoritasMongo.class})
class RepositoriosMongoIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final String JUGADOR = "11111111-1111-4111-8111-111111111111";
    private static final String OTRO = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private RepositorioEjecucionesMongo ejecuciones;

    @Autowired
    private RepositorioEstrategiasMongo estrategias;

    @Autowired
    private RepositorioFavoritasMongo favoritas;

    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void limpiar() {
        // remove y no drop: tirar la coleccion se llevaria los indices.
        mongo.remove(new Query(), EjecucionDocumento.class);
        mongo.remove(new Query(), EstrategiaDocumento.class);
        mongo.remove(new Query(), FavoritaDocumento.class);
    }

    private static Ejecucion nueva(String jugador, String mision, Instant inicio, Duration duracion, String clave) {
        return Ejecucion.nueva(UUID.randomUUID(), mision, jugador, Misiones.HEROE,
                List.of(List.of("Embate sangriento", "Ataque básico")), Escalon.HEROICO, inicio, duracion, 42L, clave);
    }

    private static ResultadoDeMision resultado() {
        return new ResultadoDeMision(true, true, 3, true, 40, 12, 9, 2,
                List.of(new ResultadoDeMision.UsoDeHabilidad("Ataque básico", 5)),
                List.of(new ResultadoDeMision.EnemigoDerrotado("Sombras Corrompidas", 2)),
                List.of(new ResultadoDeMision.MasterEnfrentado("Sombra del Olvido",
                        new Epica("Velo de Sombras", "+2 a la defensa", null, null), true)),
                72, 55.5, List.of(3, 8, 1));
    }

    private static RecompensasDeEjecucion recompensas() {
        return new RecompensasDeEjecucion(75,
                List.of(new RecompensasDeEjecucion.ObjetoGanado("Espada", "p-espada", 1)),
                List.of(new RecompensasDeEjecucion.EpicaGanada("Velo de Sombras", "Sombra del Olvido", null)),
                55.5,
                List.of(new RecompensasDeEjecucion.SinEntregar("1 Cofre de Bronce", "No esta en el catalogo")),
                List.of(new RecompensasDeEjecucion.ObjetivoEvaluado("Derrotar al jefe.", true, null)),
                true);
    }

    @Test
    @DisplayName("una ejecucion vuelve igual que se guardo, con su resultado, recompensas y pasos")
    void idaYVuelta() {
        Ejecucion guardada = ejecuciones.guardar(nueva(JUGADOR, "templo-olvidado", AHORA, Duration.ofHours(12), "k-1"));
        assertThat(guardada.version()).isZero();

        guardada.terminar(resultado(), recompensas(), true, AHORA.plus(Duration.ofHours(12)));
        guardada.pasoFallido(PasoDeLiquidacion.CORREO, "correo respondio 400");
        Ejecucion terminada = ejecuciones.guardar(guardada);
        Ejecucion leida = ejecuciones.buscar(terminada.id()).orElseThrow();

        assertThat(leida.version()).isEqualTo(1L);
        assertThat(leida.estado()).isEqualTo(EstadoEjecucion.COMPLETADA);
        assertThat(leida.heroe()).isEqualTo(Misiones.HEROE);
        assertThat(leida.estrategia()).containsExactly(List.of("Embate sangriento", "Ataque básico"));
        assertThat(leida.escalon()).isEqualTo(Escalon.HEROICO);
        assertThat(leida.semilla()).isEqualTo(42L);
        assertThat(leida.claveIdempotencia()).isEqualTo("k-1");
        assertThat(leida.iniciadaEn()).isEqualTo(AHORA);
        assertThat(leida.resultado()).isEqualTo(resultado());
        assertThat(leida.recompensas()).isEqualTo(recompensas());
        assertThat(leida.estadoDe(PasoDeLiquidacion.LIBERACION)).isEqualTo(EstadoDePaso.PENDIENTE);
        assertThat(leida.estadoDe(PasoDeLiquidacion.CORREO)).isEqualTo(EstadoDePaso.FALLIDO);
        assertThat(leida.motivoDe(PasoDeLiquidacion.CORREO)).isEqualTo("correo respondio 400");
        assertThat(leida.liquidacionPendiente()).isTrue();
    }

    @Test
    @DisplayName("version optimista: la segunda escritura sobre la misma version no pisa a la primera")
    void versionOptimista() {
        Ejecucion guardada = ejecuciones.guardar(nueva(JUGADOR, "templo-olvidado", AHORA, Duration.ofHours(1), null));
        Ejecucion copiaA = ejecuciones.buscar(guardada.id()).orElseThrow();
        Ejecucion copiaB = ejecuciones.buscar(guardada.id()).orElseThrow();

        copiaA.cancelar(AHORA.plusSeconds(10));
        ejecuciones.guardar(copiaA);
        copiaB.terminar(resultado(), recompensas(), false, AHORA.plusSeconds(20));

        assertThatThrownBy(() -> ejecuciones.guardar(copiaB)).isInstanceOf(EjecucionModificadaConcurrentemente.class);
        assertThat(ejecuciones.buscar(guardada.id()).orElseThrow().estado()).isEqualTo(EstadoEjecucion.ABANDONADA);
    }

    @Test
    @DisplayName("la base no admite dos ejecuciones en curso de la misma mision para el mismo jugador")
    void unaEnCursoPorMision() {
        Ejecucion primera = ejecuciones.guardar(nueva(JUGADOR, "templo-olvidado", AHORA, Duration.ofHours(1), null));

        assertThatThrownBy(() -> ejecuciones.guardar(nueva(JUGADOR, "templo-olvidado", AHORA, Duration.ofHours(1),
                null))).isInstanceOf(EjecucionModificadaConcurrentemente.class);
        ejecuciones.guardar(nueva(OTRO, "templo-olvidado", AHORA, Duration.ofHours(1), null));
        ejecuciones.guardar(nueva(JUGADOR, "otra-mision", AHORA, Duration.ofHours(1), null));

        primera.cancelar(AHORA.plusSeconds(5));
        ejecuciones.guardar(primera);
        ejecuciones.guardar(nueva(JUGADOR, "templo-olvidado", AHORA.plusSeconds(6), Duration.ofHours(1), null));
        assertThat(ejecuciones.delJugadorEnMision(JUGADOR, "templo-olvidado")).hasSize(2);
    }

    @Test
    @DisplayName("la clave de la matricula es unica por jugador y se encuentra por ella")
    void claveDeIdempotencia() {
        Ejecucion primera = nueva(JUGADOR, "templo-olvidado", AHORA, Duration.ofHours(1), "clave-1");
        ejecuciones.guardar(primera);

        assertThat(ejecuciones.buscarPorClave(JUGADOR, "clave-1")).map(Ejecucion::id).contains(primera.id());
        assertThat(ejecuciones.buscarPorClave(OTRO, "clave-1")).isEmpty();
        assertThatThrownBy(() -> ejecuciones.guardar(nueva(JUGADOR, "otra-mision", AHORA, Duration.ofHours(1),
                "clave-1"))).isInstanceOf(EjecucionModificadaConcurrentemente.class);
        ejecuciones.guardar(nueva(OTRO, "templo-olvidado", AHORA, Duration.ofHours(1), "clave-1"));
        // Sin clave no hay choque: el indice unico es parcial.
        ejecuciones.guardar(nueva(JUGADOR, "tercera", AHORA, Duration.ofHours(1), null));
        ejecuciones.guardar(nueva(JUGADOR, "cuarta", AHORA, Duration.ofHours(1), null));
    }

    @Test
    @DisplayName("la cola del trabajo: vencidas por orden y liquidaciones cuyo reintento ya toca")
    void colaDelTrabajo() {
        Ejecucion vencePrimero = ejecuciones.guardar(
                nueva(JUGADOR, "m-1", AHORA.minus(Duration.ofHours(3)), Duration.ofHours(1), null));
        Ejecucion venceDespues = ejecuciones.guardar(
                nueva(JUGADOR, "m-2", AHORA.minus(Duration.ofHours(3)), Duration.ofHours(2), null));
        ejecuciones.guardar(nueva(JUGADOR, "m-3", AHORA, Duration.ofHours(2), null));

        assertThat(ejecuciones.vencidas(AHORA, 10)).extracting(Ejecucion::id)
                .containsExactly(vencePrimero.id(), venceDespues.id());
        assertThat(ejecuciones.vencidas(AHORA, 1)).hasSize(1);

        Ejecucion lista = ejecuciones.buscar(vencePrimero.id()).orElseThrow();
        lista.terminar(resultado(), recompensas(), false, AHORA);
        lista = ejecuciones.guardar(lista);
        Ejecucion esperando = ejecuciones.buscar(venceDespues.id()).orElseThrow();
        esperando.terminar(resultado(), recompensas(), false, AHORA);
        esperando.reintentarMasTarde(AHORA, Duration.ofMinutes(5), "inventario no contesta");
        ejecuciones.guardar(esperando);

        assertThat(ejecuciones.conLiquidacionPendiente(AHORA, 10)).extracting(Ejecucion::id)
                .containsExactly(lista.id());
        assertThat(ejecuciones.conLiquidacionPendiente(AHORA.plus(Duration.ofMinutes(5)), 10)).hasSize(2);
        assertThat(ejecuciones.vencidas(AHORA, 10)).isEmpty();

        for (PasoDeLiquidacion paso : lista.pasosPendientes()) {
            lista.pasoHecho(paso);
        }
        ejecuciones.guardar(lista);
        assertThat(ejecuciones.conLiquidacionPendiente(AHORA.plus(Duration.ofMinutes(5)), 10))
                .extracting(Ejecucion::id).containsExactly(esperando.id());
    }

    @Test
    @DisplayName("HU-SIM-007: de dos barridos que reservan la misma ejecucion, Mongo deja pasar solo al primero")
    void reservaDeLaSimulacionConVersionOptimista() {
        Ejecucion guardada = ejecuciones.guardar(
                nueva(JUGADOR, "m-1", AHORA.minus(Duration.ofHours(3)), Duration.ofHours(1), null));
        Ejecucion delBarridoA = ejecuciones.buscar(guardada.id()).orElseThrow();
        Ejecucion delBarridoB = ejecuciones.buscar(guardada.id()).orElseThrow();

        delBarridoA.reservarParaSimular(AHORA);
        Ejecucion reservadaPorA = ejecuciones.guardar(delBarridoA);
        delBarridoB.reservarParaSimular(AHORA);

        assertThatThrownBy(() -> ejecuciones.guardar(delBarridoB))
                .isInstanceOf(EjecucionModificadaConcurrentemente.class);
        Ejecucion enLaBase = ejecuciones.buscar(guardada.id()).orElseThrow();
        assertThat(enLaBase.intentosDeSimulacion()).isEqualTo(1);
        assertThat(enLaBase.version()).isEqualTo(reservadaPorA.version());
        assertThat(enLaBase.simulacionReservadaHasta()).isNotNull();
    }

    @Test
    @DisplayName("HU-SIM-007: la cola de vencidas no incluye las reservadas ni las que esperan un reintento, y las incluye al vencer")
    void colaDeVencidasRespetaLaReserva() {
        Ejecucion libre = ejecuciones.guardar(
                nueva(JUGADOR, "m-1", AHORA.minus(Duration.ofHours(3)), Duration.ofHours(1), null));
        Ejecucion simulandose = ejecuciones.guardar(
                nueva(JUGADOR, "m-2", AHORA.minus(Duration.ofHours(3)), Duration.ofHours(1), null));
        Ejecucion esperando = ejecuciones.guardar(
                nueva(JUGADOR, "m-3", AHORA.minus(Duration.ofHours(3)), Duration.ofHours(1), null));
        simulandose.reservarParaSimular(AHORA);
        ejecuciones.guardar(simulandose);
        esperando.reservarParaSimular(AHORA);
        esperando.simulacionFallida(AHORA, Duration.ofSeconds(30), "heroes no responde");
        ejecuciones.guardar(esperando);

        assertThat(ejecuciones.vencidas(AHORA, 10)).extracting(Ejecucion::id).containsExactly(libre.id());
        assertThat(ejecuciones.vencidas(AHORA.plusSeconds(30), 10)).extracting(Ejecucion::id)
                .containsExactlyInAnyOrder(libre.id(), esperando.id());
        assertThat(ejecuciones.vencidas(AHORA.plus(Ejecucion.ARRIENDO_DE_SIMULACION), 10)).hasSize(3);
    }

    @Test
    @DisplayName("HU-SIM-007: una ejecucion guardada antes de la reserva, sin esos campos, sigue entrando en la cola de vencidas")
    void colaDeVencidasConDocumentosViejos() {
        Ejecucion vieja = nueva(JUGADOR, "m-1", AHORA.minus(Duration.ofHours(3)), Duration.ofHours(1), null);
        ejecuciones.guardar(vieja);
        // Como la dejaron las versiones anteriores: el documento no trae los campos de la reserva.
        mongo.getCollection("ejecuciones").updateOne(
                new org.bson.Document("_id", vieja.id().toString()),
                new org.bson.Document("$unset", new org.bson.Document("simulacionReservadaHasta", "")
                        .append("intentosDeSimulacion", "").append("ultimoErrorDeSimulacion", "")));

        List<Ejecucion> vencidas = ejecuciones.vencidas(AHORA, 10);

        assertThat(vencidas).extracting(Ejecucion::id).containsExactly(vieja.id());
        assertThat(vencidas.getFirst().intentosDeSimulacion()).isZero();
        assertThat(vencidas.getFirst().reclamable(AHORA)).isTrue();
    }

    @Test
    @DisplayName("las del jugador: de la mas reciente a la mas antigua, en curso e iniciadas desde")
    void delJugador() {
        Ejecucion vieja = nueva(JUGADOR, "m-1", AHORA.minus(Duration.ofDays(2)), Duration.ofHours(1), null);
        vieja.cancelar(AHORA.minus(Duration.ofDays(2)).plusSeconds(30));
        vieja = ejecuciones.guardar(vieja);
        Ejecucion reciente = ejecuciones.guardar(
                nueva(JUGADOR, "m-1", AHORA.minus(Duration.ofHours(1)), Duration.ofHours(4), null));
        ejecuciones.guardar(nueva(OTRO, "m-1", AHORA, Duration.ofHours(1), null));

        assertThat(ejecuciones.delJugador(JUGADOR)).extracting(Ejecucion::id)
                .containsExactly(reciente.id(), vieja.id());
        assertThat(ejecuciones.iniciadasDesde(JUGADOR, "m-1", AHORA.truncatedTo(ChronoUnit.DAYS))).isEqualTo(1);
        assertThat(ejecuciones.iniciadasDesde(JUGADOR, "m-1", AHORA.minus(Duration.ofDays(3)))).isEqualTo(2);
        assertThat(ejecuciones.enCursoDelJugador(OTRO)).hasSize(1);
        assertThat(ejecuciones.enCursoDelJugador(JUGADOR)).extracting(Ejecucion::id).containsExactly(reciente.id());
    }

    @Test
    @DisplayName("una estrategia por jugador y heroe: guardar otra la reemplaza")
    void estrategias() {
        estrategias.guardar(new EstrategiaGuardada(JUGADOR, "h-1", "Guerrero Armas", 1,
                List.of(List.of("Embate sangriento")), AHORA));
        estrategias.guardar(new EstrategiaGuardada(JUGADOR, "h-1", "Guerrero Armas", 2,
                List.of(List.of("Ataque básico"), List.of("Embate sangriento")), AHORA.plusSeconds(60)));

        EstrategiaGuardada leida = estrategias.buscar(JUGADOR, "h-1").orElseThrow();
        assertThat(leida.nivel()).isEqualTo(2);
        assertThat(leida.rotaciones()).containsExactly(List.of("Ataque básico"), List.of("Embate sangriento"));
        assertThat(estrategias.buscar(OTRO, "h-1")).isEmpty();
        assertThat(mongo.count(new Query(), EstrategiaDocumento.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("favoritas idempotentes por jugador")
    void favoritas() {
        favoritas.marcar(JUGADOR, "templo-olvidado", AHORA);
        favoritas.marcar(JUGADOR, "templo-olvidado", AHORA.plusSeconds(1));
        favoritas.marcar(OTRO, "otra", AHORA);

        assertThat(favoritas.delJugador(JUGADOR)).containsExactly("templo-olvidado");
        favoritas.desmarcar(JUGADOR, "templo-olvidado");
        favoritas.desmarcar(JUGADOR, "templo-olvidado");
        assertThat(favoritas.delJugador(JUGADOR)).isEmpty();
        assertThat(favoritas.delJugador(OTRO)).containsExactly("otra");
    }
}
