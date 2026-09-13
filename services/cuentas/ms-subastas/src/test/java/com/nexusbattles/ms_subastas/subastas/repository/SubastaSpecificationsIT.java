package com.nexusbattles.ms_subastas.subastas.repository;

import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.model.TipoProducto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-SUB-011. Prueba de integracion de SubastaSpecifications contra
 * PostgreSQL real -- no mocks. Justificacion: ya hubo dos bugs reales en
 * esta sesion (Specification.and(null), List.of(null)) que un mock de
 * SubastaRepository jamas habria detectado, porque el problema estaba en
 * el SQL/JPQL generado, no en la logica de negocio alrededor.
 *
 * @SpringBootTest (no @DataJpaTest): este modulo no tiene
 * spring-boot-test-autoconfigure en el classpath de test, asi que
 * @DataJpaTest/@AutoConfigureTestDatabase no compilan aqui. Se usa el mismo
 * patron ya probado en PujaConcurrenciaPostgresTest -- arranca el contexto
 * completo, mas lento que un @DataJpaTest real, pero sin depender de una
 * dependencia que habria que agregar al build.gradle compartido.
 *
 * subastaAdjudicada (estado ADJUDICADA, vencida hace 2h, precio 60.00) se
 * siembra a proposito en cada prueba junto a las activas: varias
 * Specification (precio, tiempoRestante) no filtran por estado por si
 * solas, y esa subasta cae dentro de varios rangos "activos" en apariencia
 * si no se combina con soloActivas(). Sembrarla es lo que detecta ese
 * error, no evitarla.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SubastaSpecificationsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SubastaRepository subastaRepository;

    private Subasta espadaMdj;
    private Subasta escudoJugador;
    private Subasta pocionSinPujas;
    private Subasta subastaAdjudicada;

    @BeforeEach
    void sembrarSubastas() {
        subastaRepository.deleteAll();

        espadaMdj = subastaConDatos("Espada del Alba Eterna", TipoProducto.ARMA, "Legendaria",
            new BigDecimal("150.00"), new BigDecimal("300.00"), true, 12,
            Instant.now().plusSeconds(2700), EstadoSubasta.ACTIVA);

        escudoJugador = subastaConDatos("Escudo de Roble Antiguo", TipoProducto.ARMADURA, "Comun",
            new BigDecimal("45.00"), null, false, 3,
            Instant.now().plusSeconds(64800), EstadoSubasta.ACTIVA);

        pocionSinPujas = subastaConDatos("Pocion de Vitalidad Mayor", TipoProducto.ITEM, "Rara",
            new BigDecimal("20.00"), new BigDecimal("80.00"), false, 0,
            Instant.now().plusSeconds(14400), EstadoSubasta.ACTIVA);

        // ADJUDICADA, vencida y con precio (60.00) dentro de varios rangos
        // "activos" a proposito -- ver Javadoc de la clase.
        subastaAdjudicada = subastaConDatos("Casco del Centinela", TipoProducto.ARMADURA, "Rara",
            new BigDecimal("60.00"), null, false, 6,
            Instant.now().minusSeconds(7200), EstadoSubasta.ADJUDICADA);

        subastaRepository.saveAll(List.of(espadaMdj, escudoJugador, pocionSinPujas, subastaAdjudicada));
        subastaRepository.flush();
    }

    private Subasta subastaConDatos(String nombre, TipoProducto tipo, String rareza,
                                    BigDecimal ofertaVigente, BigDecimal precioCompraInmediata,
                                    boolean esMdj, int cantidadPujas, Instant fechaFin,
                                    EstadoSubasta estado) {
        Subasta subasta = new Subasta(null, UUID.randomUUID(), UUID.randomUUID(), ofertaVigente,
            new BigDecimal("5.00"), precioCompraInmediata, null, estado, fechaFin, 0L);
        // elementoInventarioId es NOT NULL desde V4 (Edwin) -- el constructor
        // historico de 10 parametros no lo asigna, hay que ponerlo a mano o
        // el saveAll() de abajo revienta por violacion de restriccion.
        subasta.setElementoInventarioId(UUID.randomUUID().toString());
        subasta.setNombreProducto(nombre);
        subasta.setTipoProducto(tipo);
        subasta.setRareza(rareza);
        subasta.setPrecioInicial(ofertaVigente);
        subasta.setCantidadPujas(cantidadPujas);
        subasta.setEsMaestroDeJuego(esMdj);
        subasta.setFechaPublicacion(Instant.now());
        return subasta;
    }

    @Test
    void soloActivasExcluyeLasAdjudicadas() {
        var resultado = subastaRepository.findAll(
            SubastaSpecifications.soloActivas(), PageRequest.of(0, 16));

        assertEquals(3, resultado.getTotalElements());
        assertFalse(resultado.getContent().contains(subastaAdjudicada));
    }

    @Test
    void porTipoProductoFiltraExactoElTipoPedido() {
        var resultado = subastaRepository.findAll(
            SubastaSpecifications.porTipoProducto(List.of(TipoProducto.ARMA)),
            PageRequest.of(0, 16));

        assertEquals(1, resultado.getTotalElements());
        assertEquals("Espada del Alba Eterna", resultado.getContent().get(0).getNombreProducto());
    }

    @Test
    void porTipoProductoConVariosTiposTraeLaUnionDeAmbos() {
        var resultado = subastaRepository.findAll(
            SubastaSpecifications.porTipoProducto(List.of(TipoProducto.ARMA, TipoProducto.ITEM)),
            PageRequest.of(0, 16));

        assertEquals(2, resultado.getTotalElements());
    }

    @Test
    void textoLibreEncuentraPorNombreSinImportarMayusculas() {
        var resultado = subastaRepository.findAll(
            SubastaSpecifications.textoLibre("espada"), PageRequest.of(0, 16));

        assertEquals(1, resultado.getTotalElements());
    }

    @Test
    void textoLibreVacioNoFiltraNada() {
        // textoLibre("") devuelve null (texto en blanco = sin filtro). No se
        // encadena .and() directo sobre algo que puede ser null -- mismo
        // motivo por el que SubastaListadoService filtra los null antes de
        // combinar. Aqui se verifica el contrato explicitamente.
        Specification<Subasta> textoSpec = SubastaSpecifications.textoLibre("");
        assertNull(textoSpec, "un texto en blanco debe devolver null (sin filtro)");

        var resultado = subastaRepository.findAll(
            SubastaSpecifications.soloActivas(), PageRequest.of(0, 16));
        assertEquals(3, resultado.getTotalElements());
    }

    @Test
    void porTipoVentaSoloPujasExcluyeLasConCompraInmediata() {
        var resultado = subastaRepository.findAll(
            SubastaSpecifications.porTipoVenta("SOLO_PUJAS"), PageRequest.of(0, 16));

        assertEquals(2, resultado.getTotalElements());
        assertTrue(resultado.getContent().stream()
            .noneMatch(s -> s.getPrecioCompraInmediata() != null));
    }

    @Test
    void porTipoVentaCompraInmediataDisponibleSoloTraeLasQueLaTienen() {
        var resultado = subastaRepository.findAll(
            SubastaSpecifications.porTipoVenta("COMPRA_INMEDIATA_DISPONIBLE"),
            PageRequest.of(0, 16));

        assertEquals(2, resultado.getTotalElements());
    }

    @Test
    void porMetodoPagoDineroRealSoloTraeAlMaestroDeJuego() {
        var resultado = subastaRepository.findAll(
            SubastaSpecifications.porMetodoPago("DINERO_REAL"), PageRequest.of(0, 16));

        assertEquals(1, resultado.getTotalElements());
        assertTrue(resultado.getContent().get(0).isEsMaestroDeJuego());
    }

    @Test
    void porVendedorJugadoresExcluyeAlMaestroDeJuego() {
        var resultado = subastaRepository.findAll(
            SubastaSpecifications.porVendedor("JUGADORES"), PageRequest.of(0, 16));

        assertEquals(3, resultado.getTotalElements());
        assertTrue(resultado.getContent().stream().noneMatch(Subasta::isEsMaestroDeJuego));
    }

    @Test
    void precioMinimoYMaximoCombinadosAcotanElRango() {
        var spec = SubastaSpecifications.soloActivas()
            .and(SubastaSpecifications.precioMinimo(new BigDecimal("30.00")))
            .and(SubastaSpecifications.precioMaximo(new BigDecimal("100.00")));

        var resultado = subastaRepository.findAll(spec, PageRequest.of(0, 16));

        // Solo el escudo (45.00, ACTIVA) cae en [30,100] entre las activas.
        // La subasta ADJUDICADA (60.00) tambien caeria en ese rango de
        // precio -- por eso soloActivas() es necesario aqui, no opcional.
        assertEquals(1, resultado.getTotalElements());
        assertEquals("Escudo de Roble Antiguo", resultado.getContent().get(0).getNombreProducto());
    }

    @Test
    void tiempoRestanteMenos1hSoloTraeLaQueVenceEnEseRango() {
        var spec = SubastaSpecifications.soloActivas()
            .and(SubastaSpecifications.tiempoRestante("MENOS_1H", Instant.now()));

        var resultado = subastaRepository.findAll(spec, PageRequest.of(0, 16));

        // La subasta ADJUDICADA vencio hace 2 horas, asi que su fechaFin
        // tambien es <= "ahora + 1h" (matematicamente, cualquier fecha
        // pasada lo cumple) -- por eso soloActivas() es obligatorio aqui.
        assertEquals(1, resultado.getTotalElements());
        assertEquals("Espada del Alba Eterna", resultado.getContent().get(0).getNombreProducto());
    }

    @Test
    void variasSpecificationsCombinadasConAndFiltranEnConjunto() {
        var spec = SubastaSpecifications.soloActivas()
            .and(SubastaSpecifications.porVendedor("JUGADORES"))
            .and(SubastaSpecifications.porTipoVenta("COMPRA_INMEDIATA_DISPONIBLE"));

        var resultado = subastaRepository.findAll(spec, PageRequest.of(0, 16));

        assertEquals(1, resultado.getTotalElements());
        assertEquals("Pocion de Vitalidad Mayor", resultado.getContent().get(0).getNombreProducto());
    }
}
