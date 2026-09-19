package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.dto.MiParticipacionResponse;
import com.nexusbattles.ms_subastas.pujas.dto.PujaDelHistorialResponse;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClient;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoNoDisponibleException;
import com.nexusbattles.ms_subastas.pujas.creditos.ReservaCredito;
import com.nexusbattles.ms_subastas.pujas.dto.MiResumenResponse;
import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Estas consultas existen para que la pantalla deje de suponer. Antes mostraba
 * siempre "no vas ganando" y "sin puja automatica" porque el listado, que es el
 * mismo para todos, no puede responder nada sobre TI.
 */
@ExtendWith(MockitoExtension.class)
class ConsultaDeParticipacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-14T12:00:00Z");
    private static final UUID SUBASTA = UUID.randomUUID();
    private static final UUID YO = UUID.randomUUID();
    private static final UUID OTRO = UUID.randomUUID();

    @Mock
    private SubastaRepository subastaRepository;

    @Mock
    private PujaRepository pujaRepository;

    @Mock
    private PujaAutomaticaRepository pujaAutomaticaRepository;

    private CreditoClientFake creditoClient;
    private ConsultaDeParticipacionService servicio;

    @BeforeEach
    void setUp() {
        creditoClient = new CreditoClientFake(BigDecimal.ZERO, false);
        servicio = new ConsultaDeParticipacionService(subastaRepository, pujaRepository,
                pujaAutomaticaRepository, new ParametrosPuja(), Clock.fixed(AHORA, ZoneOffset.UTC),
                creditoClient);
    }

    private Subasta subastaCon(UUID mejorPostor) {
        Subasta subasta = new Subasta(SUBASTA, UUID.randomUUID(), OTRO, new BigDecimal("100"),
                new BigDecimal("10"), new BigDecimal("500"), mejorPostor, EstadoSubasta.ACTIVA,
                AHORA.plusSeconds(3600), 0L);
        lenient().when(subastaRepository.findById(SUBASTA)).thenReturn(Optional.of(subasta));
        return subasta;
    }

    private Puja puja(UUID jugador, String monto, EstadoPuja estado, long haceSegundos) {
        return new Puja(UUID.randomUUID(), SUBASTA, jugador, new BigDecimal(monto), TipoPuja.MANUAL,
                estado, AHORA.minusSeconds(haceSegundos), UUID.randomUUID().toString());
    }

    private void sinAutomatica() {
        lenient().when(pujaAutomaticaRepository.findBySubastaIdAndJugadorId(SUBASTA, YO))
                .thenReturn(Optional.empty());
    }

    private void sinPujaVigenteMia() {
        lenient().when(pujaRepository.findBySubastaIdAndEstado(SUBASTA, EstadoPuja.ACTIVA))
                .thenReturn(Optional.empty());
    }

    private void retenido(String monto) {
        lenient().when(pujaRepository.sumarMontoPorJugadorYEstado(YO, EstadoPuja.ACTIVA))
                .thenReturn(new BigDecimal(monto));
    }

    private void miUltimaPuja(Puja ultima) {
        lenient().when(pujaRepository.findFirstByJugadorIdAndSubastaIdOrderByCreadaEnDesc(YO, SUBASTA))
                .thenReturn(Optional.ofNullable(ultima));
    }

    // --- mi participacion --------------------------------------------------

    @Test
    void diceQueVasGanandoCuandoEresElMejorPostor() {
        subastaCon(YO);
        Puja mia = puja(YO, "150", EstadoPuja.ACTIVA, 60);
        when(pujaRepository.findBySubastaIdAndEstado(SUBASTA, EstadoPuja.ACTIVA)).thenReturn(Optional.of(mia));
        retenido("150");
        miUltimaPuja(mia);
        sinAutomatica();

        MiParticipacionResponse r = servicio.miParticipacion(SUBASTA, YO);

        assertTrue(r.vasGanando());
        assertFalse(r.teSuperaron());
        assertEquals(0, new BigDecimal("150").compareTo(r.tuOfertaVigente()));
    }

    /**
     * Que te superaron no es lo contrario de ir ganando: hace falta haber
     * pujado. Quien nunca pujo no va perdiendo, es que no esta participando, y
     * la pantalla no debe decirle lo mismo.
     */
    @Test
    void quienNuncaPujoNoEstaSuperado() {
        subastaCon(OTRO);
        sinPujaVigenteMia();
        retenido("0");
        miUltimaPuja(null);
        sinAutomatica();

        MiParticipacionResponse r = servicio.miParticipacion(SUBASTA, YO);

        assertFalse(r.vasGanando());
        assertFalse(r.teSuperaron(), "no pujo nunca: no se le puede decir que le superaron");
        assertNull(r.tuOfertaVigente());
    }

    @Test
    void quienPujoYYaNoVaGanandoSiEstaSuperado() {
        subastaCon(OTRO);
        sinPujaVigenteMia();
        retenido("0");
        miUltimaPuja(puja(YO, "120", EstadoPuja.SUPERADA, 300));
        sinAutomatica();

        MiParticipacionResponse r = servicio.miParticipacion(SUBASTA, YO);

        assertTrue(r.teSuperaron());
    }

    /**
     * La oferta vigente de la subasta es de otro: no puede presentarse como
     * "tu oferta" solo porque exista una puja ACTIVA.
     */
    @Test
    void laPujaVigenteDeOtroNoSeCuentaComoTuya() {
        subastaCon(OTRO);
        when(pujaRepository.findBySubastaIdAndEstado(SUBASTA, EstadoPuja.ACTIVA))
                .thenReturn(Optional.of(puja(OTRO, "200", EstadoPuja.ACTIVA, 10)));
        retenido("0");
        miUltimaPuja(null);
        sinAutomatica();

        assertNull(servicio.miParticipacion(SUBASTA, YO).tuOfertaVigente());
    }

    @Test
    void informaDelLimiteAutomaticoConfigurado() {
        subastaCon(OTRO);
        sinPujaVigenteMia();
        retenido("0");
        miUltimaPuja(null);
        when(pujaAutomaticaRepository.findBySubastaIdAndJugadorId(SUBASTA, YO))
                .thenReturn(Optional.of(new PujaAutomatica(UUID.randomUUID(), SUBASTA, YO,
                        new BigDecimal("400"), true)));

        MiParticipacionResponse r = servicio.miParticipacion(SUBASTA, YO);

        assertEquals(0, new BigDecimal("400").compareTo(r.limiteAutomatico()));
        assertTrue(r.automaticaActiva());
    }

    @Test
    void cuentaAtrasDelIntervaloDeCincoSegundos() {
        subastaCon(OTRO);
        sinPujaVigenteMia();
        retenido("0");
        sinAutomatica();
        miUltimaPuja(puja(YO, "120", EstadoPuja.SUPERADA, 2));

        assertEquals(3, servicio.miParticipacion(SUBASTA, YO).segundosParaVolverAPujar());
    }

    @Test
    void pasadoElIntervaloLaCuentaAtrasEsCero() {
        subastaCon(OTRO);
        sinPujaVigenteMia();
        retenido("0");
        sinAutomatica();
        miUltimaPuja(puja(YO, "120", EstadoPuja.SUPERADA, 30));

        assertEquals(0, servicio.miParticipacion(SUBASTA, YO).segundosParaVolverAPujar());
    }

    // --- historial ---------------------------------------------------------

    @Test
    void elHistorialMarcaCualesSonTuyas() {
        subastaCon(OTRO);
        when(pujaRepository.findBySubastaIdOrderByCreadaEnDesc(SUBASTA)).thenReturn(List.of(
                puja(OTRO, "200", EstadoPuja.ACTIVA, 10),
                puja(YO, "150", EstadoPuja.SUPERADA, 120)));

        List<PujaDelHistorialResponse> historial = servicio.historial(SUBASTA, YO);

        assertEquals(2, historial.size());
        assertFalse(historial.get(0).esTuya());
        assertTrue(historial.get(1).esTuya());
    }

    /** El historial es publico: sin sesion se ve igual, sin nada marcado como propio. */
    @Test
    void sinSesionElHistorialSeVePeroNadaEsTuyo() {
        subastaCon(OTRO);
        when(pujaRepository.findBySubastaIdOrderByCreadaEnDesc(SUBASTA))
                .thenReturn(List.of(puja(OTRO, "200", EstadoPuja.ACTIVA, 10)));

        assertFalse(servicio.historial(SUBASTA, null).get(0).esTuya());
    }

    @Test
    void consultarUnaSubastaInexistenteFalla() {
        UUID inexistente = UUID.randomUUID();
        when(subastaRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThrows(SubastaNoEncontradaException.class, () -> servicio.historial(inexistente, YO));
        assertThrows(SubastaNoEncontradaException.class, () -> servicio.miParticipacion(inexistente, YO));
    }

    // --- el saldo del resumen -----------------------------------------------

    @Test
    void elResumenTraeElSaldoDisponibleDelJugador() {
        creditoClient.acreditar(YO, new BigDecimal("450"));

        MiResumenResponse resumen = servicio.miResumen(YO);

        assertEquals(0, new BigDecimal("450").compareTo(resumen.saldoDisponible()));
    }

    /**
     * Lo importante no es que devuelva null: es que NO devuelva cero. Un cero
     * le diria al jugador que esta arruinado cuando lo unico que pasa es que no
     * se pudo preguntar, y la pantalla usa esta cifra para decidir si le deja
     * pujar: con un cero inventado le bloquearia pujas que si puede pagar.
     */
    @Test
    void siCreditosNoRespondeElSaldoViajaComoDesconocidoYNoComoCero() {
        CreditoClient roto = new CreditoClient() {
            @Override public ReservaCredito reservar(UUID j, BigDecimal m, UUID s, String k) {
                throw new UnsupportedOperationException();
            }
            @Override public void liberar(UUID reservaId) { throw new UnsupportedOperationException(); }
            @Override public void consumir(UUID reservaId, UUID vendedorId) { throw new UnsupportedOperationException(); }
            @Override public BigDecimal saldoDisponible(UUID jugadorId) {
                throw new CreditoNoDisponibleException("ms-finanzas no responde");
            }
        };
        ConsultaDeParticipacionService conCreditosCaidos = new ConsultaDeParticipacionService(
                subastaRepository, pujaRepository, pujaAutomaticaRepository, new ParametrosPuja(),
                Clock.fixed(AHORA, ZoneOffset.UTC), roto);

        when(pujaRepository.sumarMontoPorJugadorYEstado(YO, EstadoPuja.ACTIVA))
                .thenReturn(new BigDecimal("300"));

        MiResumenResponse resumen = conCreditosCaidos.miResumen(YO);

        assertNull(resumen.saldoDisponible(), "desconocido no es cero");
        // Y el resto del resumen sigue sirviendo: sale de esta misma base de
        // datos, asi que una averia de creditos no deja al jugador sin pantalla.
        assertEquals(0, new BigDecimal("300").compareTo(resumen.creditosRetenidos()));
    }
}
