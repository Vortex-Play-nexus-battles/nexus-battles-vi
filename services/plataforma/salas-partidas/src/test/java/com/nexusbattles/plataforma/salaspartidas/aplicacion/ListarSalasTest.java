package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDeSalas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caso de uso de listado de salas — HU-SAL-002, RF-JUE-002.
 *
 * <p>Primer criterio de aceptacion: el jugador elige una sala de un listado.
 * El contrato fija tres cosas que aqui se prueban: la pagina es de 16 elementos
 * por defecto -valor del componente {@code Paginacion}, que cita RNF-USA-001-,
 * se puede filtrar por modalidad y por estado, y <b>solo aparecen los tres
 * estados que la interfaz sabe pintar</b>.
 *
 * <p>Las salas privadas SI aparecen, con su insignia. Lo dice la Pantalla 2 del
 * sistema de diseno, donde dos de las ocho tarjetas son privadas. Ocultarlas
 * dejaria sin camino al rechazo 403 del contrato: solo se puede intentar entrar
 * a una sala que primero se vio.
 *
 * <p>No hay busqueda por texto, y no se prueba ninguna: se retiro del contrato
 * porque las salas no tienen nombre y ningun requisito la pide.
 */
@DisplayName("ListarSalas · caso de uso (HU-SAL-002)")
class ListarSalasTest {

    private RepositorioDeSalasEnMemoria repositorio;
    private ListarSalas listarSalas;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioDeSalasEnMemoria();
        listarSalas = new ListarSalas(repositorio);
    }

    /** Crea y guarda una sala abierta de la modalidad pedida. */
    private Sala sala(Modalidad modalidad, int maximo) {
        return repositorio.guardar(Sala.crear(
                new ParametrosDeSala(maximo, modalidad, 0, false, false, null), UUID.randomUUID()));
    }

    private Sala salaPrivada() {
        return repositorio.guardar(Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, true, null),
                UUID.randomUUID()));
    }

    @Test
    @DisplayName("devuelve las salas disponibles")
    void listaLasSalas() {
        sala(Modalidad.HASTA_SEIS, 4);
        sala(Modalidad.UNO_CONTRA_UNO, 2);

        PaginaDeSalas pagina = listarSalas.ejecutar(null, null, null, null);

        assertEquals(2, pagina.contenido().size());
    }

    @Test
    @DisplayName("las salas privadas aparecen: la Pantalla 2 las pinta con su insignia")
    void muestraLasPrivadas() {
        sala(Modalidad.HASTA_SEIS, 4);
        salaPrivada();

        PaginaDeSalas pagina = listarSalas.ejecutar(null, null, null, null);

        assertAll(
                () -> assertEquals(2, pagina.contenido().size()),
                () -> assertEquals(2, pagina.totalElementos()),
                () -> assertTrue(pagina.contenido().stream().anyMatch(Sala::privada),
                        "la privada tiene que estar en el listado"));
    }

    /**
     * Sala en un estado que el ciclo de vida de HU-SAL-002 todavia no alcanza.
     *
     * <p>Se construye con {@code rehidratar}, que es como la reconstruye el
     * almacen. No se inventa un {@code comenzar()} en el agregado: iniciar la
     * partida es HU-SAL-004 y no toca implementarlo aqui solo para tener un
     * dato de prueba.
     */
    private Sala salaEn(EstadoSala estado) {
        UUID anfitrion = UUID.randomUUID();
        return repositorio.guardar(Sala.rehidratar(UUID.randomUUID(), estado,
                Modalidad.UNO_CONTRA_UNO, 2, 0, false, false, null,
                anfitrion, Set.of(anfitrion), Instant.now()));
    }

    @Test
    @DisplayName("los estados sin tarjeta no se listan: en juego, cancelada y finalizada")
    void ocultaLasQueLaInterfazNoSabePintar() {
        sala(Modalidad.HASTA_SEIS, 4);
        salaEn(EstadoSala.EN_JUEGO);
        salaEn(EstadoSala.CANCELADA);
        salaEn(EstadoSala.FINALIZADA);

        PaginaDeSalas pagina = listarSalas.ejecutar(null, null, null, null);

        assertAll(
                () -> assertEquals(1, pagina.contenido().size()),
                () -> assertEquals(1, pagina.totalElementos()),
                () -> assertTrue(pagina.contenido().stream()
                        .allMatch(s -> s.estado().apareceEnElListado())));
    }

    @Test
    @DisplayName("sin tamano pedido, la pagina es de 16: el valor del sistema de diseno")
    void tamanoPorDefecto() {
        for (int i = 0; i < 20; i++) {
            sala(Modalidad.HASTA_SEIS, 4);
        }

        PaginaDeSalas pagina = listarSalas.ejecutar(null, null, null, null);

        assertAll(
                () -> assertEquals(0, pagina.pagina()),
                () -> assertEquals(16, pagina.tamano()),
                () -> assertEquals(16, pagina.contenido().size()),
                () -> assertEquals(20, pagina.totalElementos()),
                () -> assertEquals(2, pagina.totalPaginas()));
    }

    @Test
    @DisplayName("la segunda pagina trae el resto")
    void segundaPagina() {
        for (int i = 0; i < 20; i++) {
            sala(Modalidad.HASTA_SEIS, 4);
        }

        PaginaDeSalas pagina = listarSalas.ejecutar(1, null, null, null);

        assertAll(
                () -> assertEquals(1, pagina.pagina()),
                () -> assertEquals(4, pagina.contenido().size()));
    }

    @Test
    @DisplayName("respeta un tamano de pagina pedido")
    void tamanoPedido() {
        for (int i = 0; i < 10; i++) {
            sala(Modalidad.HASTA_SEIS, 4);
        }

        PaginaDeSalas pagina = listarSalas.ejecutar(0, 4, null, null);

        assertAll(
                () -> assertEquals(4, pagina.tamano()),
                () -> assertEquals(4, pagina.contenido().size()),
                () -> assertEquals(3, pagina.totalPaginas()));
    }

    @Test
    @DisplayName("filtra por modalidad")
    void filtraPorModalidad() {
        sala(Modalidad.HASTA_SEIS, 4);
        sala(Modalidad.UNO_CONTRA_UNO, 2);
        sala(Modalidad.UNO_CONTRA_UNO, 2);

        PaginaDeSalas pagina = listarSalas.ejecutar(null, null, Modalidad.UNO_CONTRA_UNO, null);

        assertAll(
                () -> assertEquals(2, pagina.contenido().size()),
                () -> assertTrue(pagina.contenido().stream()
                        .allMatch(s -> s.modalidad() == Modalidad.UNO_CONTRA_UNO)));
    }

    @Test
    @DisplayName("filtra por estado")
    void filtraPorEstado() {
        sala(Modalidad.HASTA_SEIS, 4);
        Sala llena = sala(Modalidad.UNO_CONTRA_UNO, 2);
        llena.unirse(UUID.randomUUID());
        repositorio.guardar(llena);

        PaginaDeSalas pagina = listarSalas.ejecutar(null, null, null, EstadoSala.LLENA);

        assertAll(
                () -> assertEquals(1, pagina.contenido().size()),
                () -> assertEquals(EstadoSala.LLENA, pagina.contenido().get(0).estado()));
    }

    @Test
    @DisplayName("sin salas devuelve una pagina vacia, no un error")
    void sinSalas() {
        PaginaDeSalas pagina = listarSalas.ejecutar(null, null, null, null);

        assertAll(
                () -> assertTrue(pagina.contenido().isEmpty()),
                () -> assertEquals(0, pagina.totalElementos()),
                () -> assertEquals(0, pagina.totalPaginas()));
    }
}
