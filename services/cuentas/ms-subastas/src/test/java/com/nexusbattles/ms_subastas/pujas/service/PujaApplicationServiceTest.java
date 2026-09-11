package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifica la orquestacion: que se lea la subasta CON lock, que el contexto
 * de participacion se arme desde el repositorio, y que se persistan tanto la
 * puja nueva como la superada. Las reglas de negocio en si ya las cubre
 * MotorPujasServiceTest, asi que aqui el motor es real (no mock) para probar
 * las dos capas juntas.
 */
@ExtendWith(MockitoExtension.class)
class PujaApplicationServiceTest {
    /** Cada puja de prueba usa su propia clave, como la enviaria un cliente distinto. */
    private static String claveUnica() {
        return UUID.randomUUID().toString();
    }


    private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");
    private static final UUID VENDEDOR = UUID.randomUUID();

    @Mock
    private SubastaRepository subastaRepository;

    @Mock
    private PujaRepository pujaRepository;

    private CreditoClientFake creditoClient;
    private PujaApplicationService servicio;

    @BeforeEach
    void setUp() {
        creditoClient = new CreditoClientFake();
        MotorPujasService motor = new MotorPujasService(creditoClient, Clock.fixed(AHORA, ZoneOffset.UTC), new ParametrosPuja());
        servicio = new PujaApplicationService(subastaRepository, pujaRepository, motor);
    }

    private Subasta subastaActiva() {
        return new Subasta(UUID.randomUUID(), UUID.randomUUID(), VENDEDOR, new BigDecimal("100"),
                new BigDecimal("10"), new BigDecimal("500"), null, EstadoSubasta.ACTIVA, AHORA.plusSeconds(86400), 0L);
    }

    @Test
    void pujarLeeLaSubastaConLockPesimistaYPersisteLaPuja() {
        Subasta subasta = subastaActiva();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        when(subastaRepository.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaRepository.findBySubastaIdAndEstado(subasta.getId(), EstadoPuja.ACTIVA)).thenReturn(Optional.empty());
        when(pujaRepository.save(any(Puja.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Puja resultado = servicio.pujar(subasta.getId(), jugador, new BigDecimal("110"), claveUnica());

        verify(subastaRepository).findByIdParaActualizar(subasta.getId());
        verify(subastaRepository, never()).findById(any());
        verify(subastaRepository).save(subasta);
        assertEquals(new BigDecimal("110"), resultado.getMonto());
        assertEquals(EstadoPuja.ACTIVA, resultado.getEstado());
    }

    @Test
    void pujarArmaElContextoDeParticipacionDesdeElRepositorio() {
        Subasta subasta = subastaActiva();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        when(subastaRepository.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaRepository.findBySubastaIdAndEstado(subasta.getId(), EstadoPuja.ACTIVA)).thenReturn(Optional.empty());
        when(pujaRepository.save(any(Puja.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.pujar(subasta.getId(), jugador, new BigDecimal("110"), claveUnica());

        verify(pujaRepository).findFirstByJugadorIdOrderByCreadaEnDesc(jugador);
        verify(pujaRepository).countByJugadorIdAndEstado(jugador, EstadoPuja.ACTIVA);
        verify(pujaRepository).contarSubastasActivasExcluyendo(jugador, EstadoPuja.ACTIVA, subasta.getId());
    }

    @Test
    void alSuperarAlPostorAnteriorSePersistenAmbasPujas() {
        Subasta subasta = subastaActiva();
        UUID primerPostor = UUID.randomUUID();
        UUID segundoPostor = UUID.randomUUID();
        creditoClient.acreditar(primerPostor, new BigDecimal("1000"));
        creditoClient.acreditar(segundoPostor, new BigDecimal("1000"));

        var reservaPrimero = creditoClient.reservar(primerPostor, new BigDecimal("110"), subasta.getId(), "previa");
        Puja pujaVigente = new Puja(UUID.randomUUID(), subasta.getId(), primerPostor, new BigDecimal("110"),
                TipoPuja.MANUAL, EstadoPuja.ACTIVA, AHORA.minusSeconds(60), reservaPrimero.id().toString());

        when(subastaRepository.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaRepository.findBySubastaIdAndEstado(subasta.getId(), EstadoPuja.ACTIVA)).thenReturn(Optional.of(pujaVigente));
        when(pujaRepository.save(any(Puja.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.pujar(subasta.getId(), segundoPostor, new BigDecimal("125"), claveUnica());

        assertEquals(EstadoPuja.SUPERADA, pujaVigente.getEstado());
        // saveAndFlush y no save: la puja anterior debe dejar de ser ACTIVA en la
        // base de datos antes de insertar la nueva, o el indice unico parcial la rechaza.
        verify(pujaRepository).saveAndFlush(pujaVigente);
        assertEquals(new BigDecimal("1000"), creditoClient.saldoDisponible(primerPostor));
    }

    @Test
    void pujarSobreUnaSubastaInexistenteFalla() {
        UUID inexistente = UUID.randomUUID();
        when(subastaRepository.findByIdParaActualizar(inexistente)).thenReturn(Optional.empty());

        assertThrows(SubastaNoEncontradaException.class,
                () -> servicio.pujar(inexistente, UUID.randomUUID(), new BigDecimal("110"), claveUnica()));
    }

    @Test
    void comprarAhoraAdjudicaYMarcaLaPujaVigenteComoSuperada() {
        Subasta subasta = subastaActiva();
        UUID postorPrevio = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(postorPrevio, new BigDecimal("1000"));
        creditoClient.acreditar(comprador, new BigDecimal("1000"));

        var reservaPrevia = creditoClient.reservar(postorPrevio, new BigDecimal("110"), subasta.getId(), "previa");
        Puja pujaVigente = new Puja(UUID.randomUUID(), subasta.getId(), postorPrevio, new BigDecimal("110"),
                TipoPuja.MANUAL, EstadoPuja.ACTIVA, AHORA.minusSeconds(60), reservaPrevia.id().toString());

        when(subastaRepository.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaRepository.findBySubastaIdAndEstado(subasta.getId(), EstadoPuja.ACTIVA)).thenReturn(Optional.of(pujaVigente));
        when(pujaRepository.save(any(Puja.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Puja ganadora = servicio.comprarAhora(subasta.getId(), comprador);

        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        assertEquals(EstadoPuja.GANADORA, ganadora.getEstado());
        assertEquals(EstadoPuja.SUPERADA, pujaVigente.getEstado());
        assertEquals(new BigDecimal("500"), creditoClient.saldoDisponible(comprador));
        assertEquals(new BigDecimal("1000"), creditoClient.saldoDisponible(postorPrevio),
                "al postor superado por la compra inmediata hay que devolverle sus creditos");
        verify(pujaRepository).saveAndFlush(pujaVigente);
    }

    @Test
    void comprarAhoraSobreUnaSubastaInexistenteFalla() {
        UUID inexistente = UUID.randomUUID();
        when(subastaRepository.findByIdParaActualizar(inexistente)).thenReturn(Optional.empty());

        assertThrows(SubastaNoEncontradaException.class,
                () -> servicio.comprarAhora(inexistente, UUID.randomUUID()));
    }

    @Test
    void cerrarPorVencimientoSinPujasDejaLaSubastaSinAdjudicacion() {
        Subasta subasta = subastaActiva();
        when(subastaRepository.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaRepository.findBySubastaIdAndEstado(subasta.getId(), EstadoPuja.ACTIVA)).thenReturn(Optional.empty());

        servicio.cerrarPorVencimiento(subasta.getId());

        assertEquals(EstadoSubasta.SIN_ADJUDICACION, subasta.getEstado());
        verify(subastaRepository).save(subasta);
    }

    @Test
    void cerrarPorVencimientoConPujaVigenteLaMarcaGanadoraYCobra() {
        Subasta subasta = subastaActiva();
        UUID postor = UUID.randomUUID();
        creditoClient.acreditar(postor, new BigDecimal("1000"));
        var reserva = creditoClient.reservar(postor, new BigDecimal("110"), subasta.getId(), "vigente");
        Puja pujaVigente = new Puja(UUID.randomUUID(), subasta.getId(), postor, new BigDecimal("110"),
                TipoPuja.MANUAL, EstadoPuja.ACTIVA, AHORA.minusSeconds(60), reserva.id().toString());

        when(subastaRepository.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaRepository.findBySubastaIdAndEstado(subasta.getId(), EstadoPuja.ACTIVA)).thenReturn(Optional.of(pujaVigente));
        when(pujaRepository.save(any(Puja.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.cerrarPorVencimiento(subasta.getId());

        assertEquals(EstadoSubasta.ADJUDICADA, subasta.getEstado());
        assertEquals(EstadoPuja.GANADORA, pujaVigente.getEstado());
        assertEquals(new BigDecimal("890"), creditoClient.saldoDisponible(postor));
    }
}
