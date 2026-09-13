package com.nexusbattles.plataforma.salaspartidas.persistencia;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.IngresoNoPermitido;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.RepositorioDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adaptador de persistencia contra una PostgreSQL de verdad — HU-SAL-001.
 *
 * <p>Se ejecuta con {@code ddl-auto=validate} a proposito: asi Hibernate compara
 * el mapeo de {@code SalaEntidad} contra las columnas que creo la migracion de
 * Flyway. Si la migracion y la entidad dejan de coincidir, esta prueba falla
 * antes que la aplicacion en produccion.
 *
 * <p><b>Sin {@code disabledWithoutDocker} a proposito.</b> heroes e inventario lo
 * usan, y por eso sus pruebas de integracion llevan tiempo saltandose sin que
 * nadie se entere: el build sale verde igual. Aqui se prefiere que falle. Una
 * prueba de integracion omitida no es una prueba que pasa, es una que no se
 * ejecuto, y la unica forma de notarlo es que duela.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import(RepositorioSalasJpa.class)
class RepositorioSalasJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID ANFITRION = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private RepositorioDeSalas repositorio;

    @Test
    @DisplayName("guarda una sala y la recupera igual que se guardo")
    void guardaYRecupera() {
        Sala sala = Sala.crear(new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 400, true, false, 2), ANFITRION);

        repositorio.guardar(sala);
        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertAll(
                () -> assertEquals(sala.id(), recuperada.id()),
                () -> assertEquals(EstadoSala.ABIERTA, recuperada.estado()),
                () -> assertEquals(Modalidad.HASTA_SEIS, recuperada.modalidad()),
                () -> assertEquals(4, recuperada.maximoParticipantes()),
                () -> assertEquals(400, recuperada.recompensaCreditos()),
                () -> assertTrue(recuperada.incluirHeroeIA()),
                () -> assertEquals(2, recuperada.tamanoEquipo()),
                () -> assertEquals(ANFITRION, recuperada.idAnfitrion()),
                () -> assertEquals(1, recuperada.ocupacion()));
    }

    @Test
    @DisplayName("una sala privada se recupera privada")
    void salaPrivada() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, true, null), ANFITRION);

        repositorio.guardar(sala);

        assertAll(
                () -> assertEquals(EstadoSala.PRIVADA,
                        repositorio.buscarPorId(sala.id()).orElseThrow().estado()),
                () -> assertTrue(repositorio.buscarPorId(sala.id()).orElseThrow().privada()));
    }

    @Test
    @DisplayName("el tamano de equipo nulo se guarda y vuelve nulo")
    void sinEquipo() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, false, false, null), ANFITRION);

        repositorio.guardar(sala);

        assertNull(repositorio.buscarPorId(sala.id()).orElseThrow().tamanoEquipo());
    }

    @Test
    @DisplayName("el momento de creacion sobrevive al viaje de ida y vuelta")
    void conservaLaFecha() {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION);

        repositorio.guardar(sala);
        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        // PostgreSQL guarda TIMESTAMPTZ con precision de microsegundos; el Instant
        // de Java tiene nanosegundos. Comparar al segundo evita un falso rojo.
        assertEquals(sala.creadaEn().getEpochSecond(), recuperada.creadaEn().getEpochSecond());
    }

    @Test
    @DisplayName("una sala que no existe no se encuentra")
    void salaInexistente() {
        assertTrue(repositorio.buscarPorId(UUID.randomUUID()).isEmpty());
    }

    // =========================================================================
    // HU-SAL-002 · RF-JUE-002 — los participantes tienen que sobrevivir al viaje
    //
    // Guardar solo el numero de ocupantes deja el aforo cuadrado pero pierde
    // QUIEN esta dentro. En cuanto una sala se rehidrata desde PostgreSQL, las
    // reglas de ingreso dejan de poder aplicarse: no hay contra que comparar.
    // Estas cinco pruebas fijan las cuatro invariantes que se rompen.
    // =========================================================================

    private static final UUID ANA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BRUNO = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    /** Sala de cuatro cupos con el anfitrion y los dos jugadores dentro. */
    private Sala salaConTresDentro() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION);
        sala.unirse(ANA);
        sala.unirse(BRUNO);
        return sala;
    }

    @Test
    @DisplayName("una sala con varios participantes conserva sus identidades al rehidratarse")
    void conservaLosParticipantes() {
        Sala sala = salaConTresDentro();

        repositorio.guardar(sala);
        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertAll(
                () -> assertEquals(3, recuperada.ocupacion()),
                () -> assertEquals(3, recuperada.participantes().size()),
                () -> assertTrue(recuperada.participantes().contains(ANFITRION), "el anfitrion"),
                () -> assertTrue(recuperada.participantes().contains(ANA), "Ana"),
                () -> assertTrue(recuperada.participantes().contains(BRUNO), "Bruno"));
    }

    @Test
    @DisplayName("tras rehidratar sigue detectando a quien ya esta dentro")
    void detectaDuplicadosTrasRehidratar() {
        Sala sala = salaConTresDentro();
        repositorio.guardar(sala);

        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertThrows(IngresoNoPermitido.class, () -> recuperada.unirse(ANA));
    }

    @Test
    @DisplayName("tras rehidratar el anfitrion sigue contando como participante")
    void elAnfitrionSobreviveAlViaje() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION);
        repositorio.guardar(sala);

        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertThrows(IngresoNoPermitido.class, () -> recuperada.unirse(ANFITRION));
    }

    @Test
    @DisplayName("tras rehidratar el ultimo cupo sigue llevando la sala a LLENA")
    void elAforoSigueCuadrandoTrasRehidratar() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION);
        repositorio.guardar(sala);

        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();
        recuperada.unirse(ANA);

        assertAll(
                () -> assertEquals(2, recuperada.ocupacion()),
                () -> assertEquals(EstadoSala.LLENA, recuperada.estado()),
                () -> assertTrue(recuperada.participantes().contains(ANA)));
    }

    @Test
    @DisplayName("una sala guardada llena sigue rechazando tras rehidratarse")
    void salaLlenaSigueRechazando() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION);
        sala.unirse(ANA);
        repositorio.guardar(sala);

        Sala recuperada = repositorio.buscarPorId(sala.id()).orElseThrow();

        assertAll(
                () -> assertEquals(EstadoSala.LLENA, recuperada.estado()),
                () -> assertThrows(IngresoNoPermitido.class, () -> recuperada.unirse(BRUNO)));
    }

    // =========================================================================
    // Listado — HU-SAL-002, RF-JUE-002. La JPQL de SalasSpringData.listar con
    // sus dos filtros anulables y la paginacion se ejecuta aqui contra
    // PostgreSQL de verdad: un `:parametro IS NULL` que funciona en H2 puede
    // no funcionar en PostgreSQL, y hasta ahora nadie la habia ejecutado.
    // =========================================================================

    @Test
    @DisplayName("listar sin filtros trae solo los estados del listado: abiertas, llenas y privadas")
    void listarSinFiltros() {
        guardarConEstado(EstadoSala.ABIERTA, Modalidad.HASTA_SEIS);
        guardarConEstado(EstadoSala.LLENA, Modalidad.UNO_CONTRA_UNO);
        guardarConEstado(EstadoSala.PRIVADA, Modalidad.CONTRA_IA);
        guardarConEstado(EstadoSala.EN_JUEGO, Modalidad.HASTA_SEIS);
        guardarConEstado(EstadoSala.CANCELADA, Modalidad.HASTA_SEIS);
        guardarConEstado(EstadoSala.FINALIZADA, Modalidad.UNO_CONTRA_UNO);

        PaginaDeSalas pagina = repositorio.listar(null, null, 0, 16);

        assertAll(
                () -> assertEquals(3, pagina.totalElementos()),
                () -> assertEquals(3, pagina.contenido().size()),
                () -> assertTrue(pagina.contenido().stream()
                        .allMatch(s -> s.estado().apareceEnElListado())),
                () -> assertTrue(pagina.contenido().stream()
                        .anyMatch(s -> s.estado() == EstadoSala.PRIVADA), "las privadas si aparecen"),
                () -> assertEquals(0, pagina.pagina()),
                () -> assertEquals(16, pagina.tamano()),
                () -> assertEquals(1, pagina.totalPaginas()));
    }

    @Test
    @DisplayName("listar filtra por modalidad y deja pasar el resto de estados del listado")
    void listarPorModalidad() {
        guardarConEstado(EstadoSala.ABIERTA, Modalidad.HASTA_SEIS);
        guardarConEstado(EstadoSala.LLENA, Modalidad.HASTA_SEIS);
        guardarConEstado(EstadoSala.ABIERTA, Modalidad.UNO_CONTRA_UNO);
        guardarConEstado(EstadoSala.EN_JUEGO, Modalidad.HASTA_SEIS);

        PaginaDeSalas pagina = repositorio.listar(Modalidad.HASTA_SEIS, null, 0, 16);

        assertAll(
                () -> assertEquals(2, pagina.totalElementos()),
                () -> assertTrue(pagina.contenido().stream()
                        .allMatch(s -> s.modalidad() == Modalidad.HASTA_SEIS)),
                () -> assertTrue(pagina.contenido().stream()
                        .noneMatch(s -> s.estado() == EstadoSala.EN_JUEGO)));
    }

    @Test
    @DisplayName("listar filtra por estado, tambien cuando el estado pedido es privada")
    void listarPorEstado() {
        guardarConEstado(EstadoSala.ABIERTA, Modalidad.HASTA_SEIS);
        guardarConEstado(EstadoSala.PRIVADA, Modalidad.HASTA_SEIS);
        guardarConEstado(EstadoSala.PRIVADA, Modalidad.UNO_CONTRA_UNO);
        guardarConEstado(EstadoSala.LLENA, Modalidad.UNO_CONTRA_UNO);

        PaginaDeSalas privadas = repositorio.listar(null, EstadoSala.PRIVADA, 0, 16);
        PaginaDeSalas llenas = repositorio.listar(null, EstadoSala.LLENA, 0, 16);

        assertAll(
                () -> assertEquals(2, privadas.totalElementos()),
                () -> assertTrue(privadas.contenido().stream()
                        .allMatch(s -> s.estado() == EstadoSala.PRIVADA)),
                () -> assertEquals(1, llenas.totalElementos()),
                () -> assertEquals(EstadoSala.LLENA, llenas.contenido().get(0).estado()));
    }

    @Test
    @DisplayName("un estado que no es del listado no se cuela aunque se pida explicitamente")
    void listarNoDevuelveEstadosFueraDelListado() {
        guardarConEstado(EstadoSala.EN_JUEGO, Modalidad.HASTA_SEIS);

        PaginaDeSalas pagina = repositorio.listar(null, EstadoSala.EN_JUEGO, 0, 16);

        assertEquals(0, pagina.totalElementos());
    }

    @Test
    @DisplayName("listar combina modalidad y estado")
    void listarPorModalidadYEstado() {
        guardarConEstado(EstadoSala.ABIERTA, Modalidad.HASTA_SEIS);
        guardarConEstado(EstadoSala.LLENA, Modalidad.HASTA_SEIS);
        guardarConEstado(EstadoSala.ABIERTA, Modalidad.UNO_CONTRA_UNO);

        PaginaDeSalas pagina = repositorio.listar(Modalidad.HASTA_SEIS, EstadoSala.ABIERTA, 0, 16);

        assertAll(
                () -> assertEquals(1, pagina.totalElementos()),
                () -> assertEquals(Modalidad.HASTA_SEIS, pagina.contenido().get(0).modalidad()),
                () -> assertEquals(EstadoSala.ABIERTA, pagina.contenido().get(0).estado()));
    }

    @Test
    @DisplayName("listar pagina en la base de datos: 20 salas son 16 y 4 con el tamano de diseno")
    void listarPagina() {
        for (int i = 0; i < 20; i++) {
            guardarConEstado(EstadoSala.ABIERTA, Modalidad.HASTA_SEIS);
        }

        PaginaDeSalas primera = repositorio.listar(null, null, 0, PaginaDeSalas.TAMANO_POR_DEFECTO);
        PaginaDeSalas segunda = repositorio.listar(null, null, 1, PaginaDeSalas.TAMANO_POR_DEFECTO);
        PaginaDeSalas vacia = repositorio.listar(null, null, 2, PaginaDeSalas.TAMANO_POR_DEFECTO);

        assertAll(
                () -> assertEquals(16, primera.contenido().size()),
                () -> assertEquals(4, segunda.contenido().size()),
                () -> assertEquals(0, vacia.contenido().size()),
                () -> assertEquals(20, primera.totalElementos()),
                () -> assertEquals(2, primera.totalPaginas()),
                () -> assertEquals(1, segunda.pagina()),
                () -> assertEquals(0, java.util.stream.Stream.concat(
                                primera.contenido().stream(), segunda.contenido().stream())
                        .map(Sala::id).distinct().count() - 20,
                        "las dos paginas no repiten ni omiten salas"));
    }

    @Test
    @DisplayName("la version de concurrencia sobrevive al viaje de ida y vuelta y avanza al escribir")
    void laVersionAvanzaAlEscribir() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION);
        repositorio.guardar(sala);

        Sala primeraLectura = repositorio.buscarPorId(sala.id()).orElseThrow();
        long versionInicial = primeraLectura.version();
        primeraLectura.unirse(ANA);
        repositorio.guardar(primeraLectura);
        Sala segundaLectura = repositorio.buscarPorId(sala.id()).orElseThrow();

        // Lo que garantiza el bloqueo optimista es que la marca viaja intacta
        // de la lectura a la escritura y avanza exactamente en uno por cada
        // escritura que llega a la base. El valor con el que nace la fila lo
        // decide Hibernate (ver RepositorioSalasJpa.guardar), no el dominio.
        assertAll(
                () -> assertTrue(versionInicial >= sala.version(),
                        "la marca nunca retrocede: dominio " + sala.version() + ", base " + versionInicial),
                () -> assertEquals(versionInicial + 1, segundaLectura.version(),
                        "una escritura, una unidad mas"),
                () -> assertEquals(2, segundaLectura.ocupacion()));
    }

    /**
     * Sala en el estado pedido, tal como la reconstruye la persistencia. Se
     * usa {@code rehidratar} porque {@code crear} solo produce ABIERTA o
     * PRIVADA y el listado tiene que probarse con los seis estados.
     */
    private void guardarConEstado(EstadoSala estado, Modalidad modalidad) {
        int maximo = modalidad == Modalidad.HASTA_SEIS ? 6 : 2;
        repositorio.guardar(Sala.rehidratar(UUID.randomUUID(), estado, modalidad, maximo, 0,
                false, estado == EstadoSala.PRIVADA, null, ANFITRION, java.util.Set.of(),
                java.time.Instant.now()));
    }
}
