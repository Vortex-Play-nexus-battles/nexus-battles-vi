package com.nexusbattles.ms_subastas.subastas.repository;

import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU-SUB-011. Prueba de integracion de SubastaRepository.buscarSugeridasPorTexto
 * contra PostgreSQL real -- el bug real que motivo este archivo (DISTINCT +
 * ORDER BY, SQLState 42P10) solo aparecio al ejecutar contra Postgres de
 * verdad, nunca se habria visto con un mock del repositorio.
 *
 * "Espada Oxidada" se siembra deliberadamente ADJUDICADA con mas pujas que
 * las demas -- si el filtro de estado se "colara" (como ya paso una vez con
 * precioMinimo/tiempoRestante sin soloActivas), esta prueba lo detectaria
 * de inmediato: apareceria primera por popularidad en vez de estar ausente.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SubastaRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SubastaRepository subastaRepository;

    @BeforeEach
    void sembrarSubastas() {
        subastaRepository.deleteAll();

        Subasta espadaDeHielo = nuevaSubasta("Espada de Hielo", EstadoSubasta.ACTIVA, 15);
        Subasta espadaDeFuego = nuevaSubasta("Espada de Fuego", EstadoSubasta.ACTIVA, 5);
        Subasta escudoDeRoble = nuevaSubasta("Escudo de Roble", EstadoSubasta.ACTIVA, 2);
        Subasta espadaOxidada = nuevaSubasta("Espada Oxidada", EstadoSubasta.ADJUDICADA, 100);

        subastaRepository.saveAll(List.of(espadaDeHielo, espadaDeFuego, escudoDeRoble, espadaOxidada));
        subastaRepository.flush();
    }

    private Subasta nuevaSubasta(String nombre, EstadoSubasta estado, int cantidadPujas) {
        Subasta subasta = new Subasta(null, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("50.00"), new BigDecimal("5.00"), null, null,
            estado, Instant.now().plusSeconds(3600), 0L);
        // elementoInventarioId es NOT NULL desde V4 (Edwin) -- ver mismo
        // comentario en SubastaSpecificationsIT.
        subasta.setElementoInventarioId(UUID.randomUUID().toString());
        subasta.setNombreProducto(nombre);
        subasta.setCantidadPujas(cantidadPujas);
        subasta.setFechaPublicacion(Instant.now());
        return subasta;
    }

    @Test
    void encuentraCoincidenciasPorTextoOrdenadasPorPopularidad() {
        List<Subasta> resultado = subastaRepository.buscarSugeridasPorTexto(
            "espada", PageRequest.of(0, 10));

        assertEquals(2, resultado.size(),
            "solo 2: Espada de Hielo y Espada de Fuego (ACTIVA) -- Espada Oxidada esta ADJUDICADA");
        assertEquals("Espada de Hielo", resultado.get(0).getNombreProducto());
        assertEquals("Espada de Fuego", resultado.get(1).getNombreProducto());
    }

    @Test
    void laBusquedaNoDistingueMayusculasYMinusculas() {
        List<Subasta> resultado = subastaRepository.buscarSugeridasPorTexto(
            "ESPADA", PageRequest.of(0, 10));

        assertEquals(2, resultado.size());
    }

    @Test
    void respetaElLimiteDelPageable() {
        List<Subasta> resultado = subastaRepository.buscarSugeridasPorTexto(
            "espada", PageRequest.of(0, 1));

        assertEquals(1, resultado.size());
        assertEquals("Espada de Hielo", resultado.get(0).getNombreProducto(),
            "con limite 1, debe traer la de mayor cantidadPujas, no cualquiera");
    }

    @Test
    void unaSubastaAdjudicadaNuncaApareceAunqueCoincidaYTengaMasPujas() {
        List<Subasta> resultado = subastaRepository.buscarSugeridasPorTexto(
            "espada", PageRequest.of(0, 10));

        assertTrue(resultado.stream().noneMatch(s -> "Espada Oxidada".equals(s.getNombreProducto())),
            "Espada Oxidada tiene 100 pujas (la mas alta) pero esta ADJUDICADA -- no debe aparecer");
    }

    @Test
    void sinCoincidenciasDevuelveListaVacia() {
        List<Subasta> resultado = subastaRepository.buscarSugeridasPorTexto(
            "hacha", PageRequest.of(0, 10));

        assertTrue(resultado.isEmpty());
    }

    @Test
    void rechazaDosSubastasActivasDelMismoElemento() {
        Subasta primera = subastaRepository.saveAndFlush(
            nuevaSubasta("Unidad", EstadoSubasta.ACTIVA, 0));
        Subasta duplicada = nuevaSubasta("Unidad", EstadoSubasta.ACTIVA, 0);
        duplicada.setElementoInventarioId(primera.getElementoInventarioId());

        assertThrows(DataIntegrityViolationException.class,
            () -> subastaRepository.saveAndFlush(duplicada));
    }

    @Test
    void permiteUnidadesDistintasDelMismoProductoActivas() {
        Subasta primera = subastaRepository.saveAndFlush(
            nuevaSubasta("Producto", EstadoSubasta.ACTIVA, 0));
        Subasta segunda = nuevaSubasta("Producto", EstadoSubasta.ACTIVA, 0);
        segunda.setProductoId(primera.getProductoId());

        assertDoesNotThrow(() -> subastaRepository.saveAndFlush(segunda));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoSubasta.class, names = {"ADJUDICADA", "SIN_ADJUDICACION"})
    void permiteHistorialYRepublicarTrasFinalizar(EstadoSubasta estadoFinal) {
        Subasta primera = subastaRepository.saveAndFlush(
            nuevaSubasta("Unidad", EstadoSubasta.ACTIVA, 0));
        primera.setEstado(estadoFinal);
        subastaRepository.saveAndFlush(primera);

        Subasta historica = nuevaSubasta("Unidad", estadoFinal, 0);
        historica.setElementoInventarioId(primera.getElementoInventarioId());
        historica.setProductoId(primera.getProductoId());
        assertDoesNotThrow(() -> subastaRepository.saveAndFlush(historica));

        Subasta nueva = nuevaSubasta("Unidad", EstadoSubasta.ACTIVA, 0);
        nueva.setElementoInventarioId(primera.getElementoInventarioId());
        nueva.setProductoId(primera.getProductoId());
        assertDoesNotThrow(() -> subastaRepository.saveAndFlush(nueva));

        historica.setEstado(EstadoSubasta.ACTIVA);
        assertThrows(DataIntegrityViolationException.class,
            () -> subastaRepository.saveAndFlush(historica));
    }
}
