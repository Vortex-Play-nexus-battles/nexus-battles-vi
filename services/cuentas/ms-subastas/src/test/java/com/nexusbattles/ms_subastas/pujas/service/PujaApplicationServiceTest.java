package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.notificaciones.NotificacionOutbox;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.dto.PujaResponse;
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
import java.util.List;
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

    @Mock
    private NotificacionOutbox outbox;

    private CreditoClientFake creditoClient;
    private PujaApplicationService servicio;

    @BeforeEach
    void setUp() {
        creditoClient = new CreditoClientFake();
        MotorPujasService motor = new MotorPujasService(creditoClient, Clock.fixed(AHORA, ZoneOffset.UTC), new ParametrosPuja());
        servicio = new PujaApplicationService(subastaRepository, pujaRepository, motor, outbox);
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

        verify(pujaRepository).findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(jugador, subasta.getId());
        verify(pujaRepository).countByJugadorIdAndEstado(jugador, EstadoPuja.ACTIVA);
        verify(pujaRepository).contarSubastasActivasExcluyendo(jugador, EstadoPuja.ACTIVA, subasta.getId());
    }

    /**
     * El intervalo minimo es por jugador Y subasta, no por jugador a secas.
     * Con la consulta global, pujar en una subasta bloqueaba al jugador en
     * TODAS las demas durante 5 s: con 10 subastas simultaneas (el tope que
     * permite la propia HU) solo alcanzaba a pujar en una cada 5 s, y su propia
     * puja automatica en una subasta le bloqueaba pujar a mano en otra.
     */
    @Test
    void pujarEnUnaSubastaNoBloqueaPujarEnOtraDistinta() {
        Subasta primera = subastaActiva();
        Subasta segunda = subastaActiva();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        // En la segunda subasta no ha pujado nunca, asi que no hay intervalo que
        // esperar — aunque acabe de pujar en la primera hace un segundo.
        when(pujaRepository.findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(jugador, segunda.getId()))
                .thenReturn(Optional.empty());
        when(subastaRepository.findByIdParaActualizar(segunda.getId())).thenReturn(Optional.of(segunda));
        when(pujaRepository.findBySubastaIdAndEstado(segunda.getId(), EstadoPuja.ACTIVA)).thenReturn(Optional.empty());
        when(pujaRepository.save(any(Puja.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Puja enLaSegunda = servicio.pujar(segunda.getId(), jugador, new BigDecimal("110"), claveUnica());

        assertEquals(EstadoPuja.ACTIVA, enLaSegunda.getEstado());
        // La clave: la consulta del intervalo va acotada a ESTA subasta, asi que
        // lo que el jugador hiciera en la primera no entra en la decision.
        verify(pujaRepository).findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(jugador, segunda.getId());
        verify(pujaRepository, never()).findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(jugador, primera.getId());
    }

    /** Pero dentro de la MISMA subasta el freno sigue en pie. */
    @Test
    void pujarDosVecesSeguidasEnLaMismaSubastaSigueRechazandose() {
        Subasta subasta = subastaActiva();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        Puja haceUnSegundo = new Puja(UUID.randomUUID(), subasta.getId(), jugador, new BigDecimal("110"),
                TipoPuja.MANUAL, EstadoPuja.SUPERADA, AHORA.minusSeconds(1), UUID.randomUUID().toString());

        when(pujaRepository.findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(jugador, subasta.getId()))
                .thenReturn(Optional.of(haceUnSegundo));
        when(subastaRepository.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaRepository.findBySubastaIdAndEstado(subasta.getId(), EstadoPuja.ACTIVA)).thenReturn(Optional.empty());

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> servicio.pujar(subasta.getId(), jugador, new BigDecimal("120"), claveUnica()));

        assertEquals(PujaRechazadaException.Motivo.INTERVALO_MINIMO_NO_CUMPLIDO, ex.getMotivo());
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
    void comprarAhoraEncolaElAvisoParaTodosLosQueHabianPujado() {
        Subasta subasta = subastaActiva();
        UUID postorPrevio = UUID.randomUUID();
        UUID postorAntiguo = UUID.randomUUID();
        UUID comprador = UUID.randomUUID();
        creditoClient.acreditar(postorPrevio, new BigDecimal("1000"));
        creditoClient.acreditar(comprador, new BigDecimal("1000"));

        var reservaPrevia = creditoClient.reservar(postorPrevio, new BigDecimal("110"), subasta.getId(), "previa");
        Puja pujaVigente = new Puja(UUID.randomUUID(), subasta.getId(), postorPrevio, new BigDecimal("110"),
                TipoPuja.MANUAL, EstadoPuja.ACTIVA, AHORA.minusSeconds(60), reservaPrevia.id().toString());

        when(subastaRepository.findByIdParaActualizar(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaRepository.findBySubastaIdAndEstado(subasta.getId(), EstadoPuja.ACTIVA)).thenReturn(Optional.of(pujaVigente));
        when(pujaRepository.save(any(Puja.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(pujaRepository.findDistinctJugadorIdBySubastaId(subasta.getId()))
                .thenReturn(List.of(postorPrevio, postorAntiguo));

        servicio.comprarAhora(subasta.getId(), comprador);

        // Incluye al postor antiguo que ya estaba SUPERADA: la historia dice
        // "notificando a quienes hubieran pujado", no solo al que iba ganando.
        verify(outbox).avisarCierrePorCompraInmediata(subasta.getId(),
                List.of(postorPrevio, postorAntiguo), comprador);
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

    @Test
    void listarPujasPorJugadorYEstadoDevuelvePujasMapeadasADto() {
        UUID jugador = UUID.randomUUID();
        UUID subastaId = UUID.randomUUID();
        Puja puja = new Puja(UUID.randomUUID(), subastaId, jugador, new BigDecimal("250"),
                TipoPuja.MANUAL, EstadoPuja.ACTIVA, AHORA, "reserva-1");

        when(pujaRepository.findByJugadorIdAndEstadoOrderByCreadaEnDesc(jugador, EstadoPuja.ACTIVA))
                .thenReturn(List.of(puja));

        List<PujaResponse> resultado = servicio.listarPujasPorJugadorYEstado(jugador, EstadoPuja.ACTIVA);

        assertEquals(1, resultado.size());
        assertEquals(puja.getId(), resultado.get(0).id());
        assertEquals(puja.getSubastaId(), resultado.get(0).subastaId());
        assertEquals(new BigDecimal("250"), resultado.get(0).monto());
        assertEquals(EstadoPuja.ACTIVA, resultado.get(0).estado());
        verify(pujaRepository).findByJugadorIdAndEstadoOrderByCreadaEnDesc(jugador, EstadoPuja.ACTIVA);
    }

    @Test
    void findByJugadorIdAndEstadoDelegaEnRepositorio() {
        UUID jugador = UUID.randomUUID();
        UUID subastaId = UUID.randomUUID();
        Puja puja = new Puja(UUID.randomUUID(), subastaId, jugador, new BigDecimal("400"),
                TipoPuja.AUTOMATICA, EstadoPuja.ACTIVA, AHORA, "reserva-2");

        when(pujaRepository.findByJugadorIdAndEstado(jugador, EstadoPuja.ACTIVA))
                .thenReturn(List.of(puja));

        List<Puja> resultado = servicio.findByJugadorIdAndEstado(jugador, EstadoPuja.ACTIVA);

        assertEquals(1, resultado.size());
        assertEquals(puja.getId(), resultado.get(0).getId());
        verify(pujaRepository).findByJugadorIdAndEstado(jugador, EstadoPuja.ACTIVA);
    }

    @Test
    void listarPujasPorJugadorYEstadoLanzaNullPointerSiArgumentosSonNulos() {
        assertThrows(NullPointerException.class, () -> servicio.listarPujasPorJugadorYEstado(null, EstadoPuja.ACTIVA));
        assertThrows(NullPointerException.class, () -> servicio.listarPujasPorJugadorYEstado(UUID.randomUUID(), null));
    }

    @Test
    void findByJugadorIdAndEstadoLanzaNullPointerSiArgumentosSonNulos() {
        assertThrows(NullPointerException.class, () -> servicio.findByJugadorIdAndEstado(null, EstadoPuja.ACTIVA));
        assertThrows(NullPointerException.class, () -> servicio.findByJugadorIdAndEstado(UUID.randomUUID(), null));
    }
}
