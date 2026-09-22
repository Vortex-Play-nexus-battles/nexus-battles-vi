package com.nexusbattles.ms_subastas.pujas.service;

import com.nexusbattles.ms_subastas.pujas.creditos.CreditoClientFake;
import com.nexusbattles.ms_subastas.pujas.model.PujaAutomatica;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre la orquestacion de la puja automatica: que el saldo se consulte a
 * ms-finanzas y no se invente, que reconfigurar sea un upsert (la tabla tiene
 * un unico por subasta y jugador, insertar dos veces reventaria), y que
 * desactivar sea idempotente. Las reglas en si ya las cubre
 * MotorPujaAutomaticaServiceTest, asi que aqui el motor es real.
 */
@ExtendWith(MockitoExtension.class)
class PujaAutomaticaApplicationServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-13T12:00:00Z");
    private static final UUID VENDEDOR = UUID.randomUUID();

    @Mock
    private SubastaRepository subastaRepository;

    @Mock
    private PujaAutomaticaRepository pujaAutomaticaRepository;

    private CreditoClientFake creditoClient;
    private PujaAutomaticaApplicationService servicio;

    @BeforeEach
    void setUp() {
        creditoClient = new CreditoClientFake();
        MotorPujaAutomaticaService motor = new MotorPujaAutomaticaService(
                Clock.fixed(AHORA, ZoneOffset.UTC), new ParametrosPuja());
        servicio = new PujaAutomaticaApplicationService(
                subastaRepository, pujaAutomaticaRepository, motor, creditoClient);
    }

    private Subasta subastaActiva() {
        return new Subasta(UUID.randomUUID(), UUID.randomUUID(), VENDEDOR, new BigDecimal("100"),
                new BigDecimal("10"), new BigDecimal("500"), null, EstadoSubasta.ACTIVA,
                AHORA.plusSeconds(86400), 0L);
    }

    @Test
    void configurarGuardaLaPrimeraPujaAutomaticaDelJugador() {
        Subasta subasta = subastaActiva();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));

        when(subastaRepository.findById(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaAutomaticaRepository.findBySubastaIdAndJugadorId(subasta.getId(), jugador))
                .thenReturn(Optional.empty());
        when(pujaAutomaticaRepository.save(any(PujaAutomatica.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        PujaAutomatica configurada = servicio.configurar(subasta.getId(), jugador, new BigDecimal("300"));

        assertEquals(new BigDecimal("300"), configurada.getLimite());
        assertEquals(jugador, configurada.getJugadorId());
        assertTrue(configurada.isActiva());
    }

    /**
     * El contrato declara el PUT idempotente por jugador y subasta, y la tabla
     * lo respalda con un unico (subasta_id, jugador_id). Si esto insertara una
     * fila nueva, la segunda configuracion moriria contra esa restriccion.
     */
    @Test
    void reconfigurarActualizaLaExistenteEnVezDeInsertarOtra() {
        Subasta subasta = subastaActiva();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        PujaAutomatica existente = new PujaAutomatica(UUID.randomUUID(), subasta.getId(), jugador,
                new BigDecimal("200"), true);

        when(subastaRepository.findById(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaAutomaticaRepository.findBySubastaIdAndJugadorId(subasta.getId(), jugador))
                .thenReturn(Optional.of(existente));
        when(pujaAutomaticaRepository.save(any(PujaAutomatica.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        PujaAutomatica resultado = servicio.configurar(subasta.getId(), jugador, new BigDecimal("400"));

        assertSame(existente, resultado, "debe reutilizar la fila, no crear una segunda");
        assertEquals(new BigDecimal("400"), existente.getLimite());
    }

    /**
     * Subir el limite despues de que el motor la desactivo por quedarse corta
     * es la forma natural de volver a competir; obligar a borrar y crear seria
     * gratuito.
     */
    @Test
    void reconfigurarReactivaUnaPujaAutomaticaQueSeHabiaAgotado() {
        Subasta subasta = subastaActiva();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        PujaAutomatica agotada = new PujaAutomatica(UUID.randomUUID(), subasta.getId(), jugador,
                new BigDecimal("105"), false);

        when(subastaRepository.findById(subasta.getId())).thenReturn(Optional.of(subasta));
        when(pujaAutomaticaRepository.findBySubastaIdAndJugadorId(subasta.getId(), jugador))
                .thenReturn(Optional.of(agotada));
        when(pujaAutomaticaRepository.save(any(PujaAutomatica.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.configurar(subasta.getId(), jugador, new BigDecimal("400"));

        assertTrue(agotada.isActiva());
    }

    @Test
    void configurarSobreUnaSubastaInexistenteFalla() {
        UUID inexistente = UUID.randomUUID();
        when(subastaRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThrows(SubastaNoEncontradaException.class,
                () -> servicio.configurar(inexistente, UUID.randomUUID(), new BigDecimal("300")));
    }

    @Test
    void unLimiteQueNuncaLlegariaAPujarSeRechaza() {
        Subasta subasta = subastaActiva();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("1000"));
        when(subastaRepository.findById(subasta.getId())).thenReturn(Optional.of(subasta));

        // La siguiente oferta valida es 110; un limite de 105 no alcanza nunca.
        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> servicio.configurar(subasta.getId(), jugador, new BigDecimal("105")));

        assertEquals(PujaRechazadaException.Motivo.LIMITE_AUTOMATICO_INALCANZABLE, ex.getMotivo());
        verify(pujaAutomaticaRepository, never()).save(any());
    }

    /**
     * El saldo lo pone ms-finanzas, no el cliente: si viniera en el cuerpo,
     * cualquiera podria configurar un limite que no puede pagar.
     */
    @Test
    void unLimiteQueElSaldoNoCubreSeRechaza() {
        Subasta subasta = subastaActiva();
        UUID jugador = UUID.randomUUID();
        creditoClient.acreditar(jugador, new BigDecimal("150"));
        when(subastaRepository.findById(subasta.getId())).thenReturn(Optional.of(subasta));

        PujaRechazadaException ex = assertThrows(PujaRechazadaException.class,
                () -> servicio.configurar(subasta.getId(), jugador, new BigDecimal("400")));

        assertEquals(PujaRechazadaException.Motivo.SALDO_INSUFICIENTE_PARA_LIMITE, ex.getMotivo());
        verify(pujaAutomaticaRepository, never()).save(any());
    }

    @Test
    void desactivarApagaLaPujaAutomaticaSinBorrarla() {
        UUID subastaId = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();
        PujaAutomatica activa = new PujaAutomatica(UUID.randomUUID(), subastaId, jugador,
                new BigDecimal("300"), true);

        when(subastaRepository.existsById(subastaId)).thenReturn(true);
        when(pujaAutomaticaRepository.findBySubastaIdAndJugadorId(subastaId, jugador))
                .thenReturn(Optional.of(activa));

        servicio.desactivar(subastaId, jugador);

        assertFalse(activa.isActiva());
        verify(pujaAutomaticaRepository).save(activa);
        verify(pujaAutomaticaRepository, never()).delete(any());
    }

    /**
     * DELETE describe un estado final ("ninguna puja automatica activa"), no una
     * accion: si ya se cumplia, la peticion prospera igual. Reintentarla tras un
     * timeout no puede convertirse en un 404.
     */
    @Test
    void desactivarSinHaberConfiguradoNingunaNoEsUnError() {
        UUID subastaId = UUID.randomUUID();
        UUID jugador = UUID.randomUUID();

        when(subastaRepository.existsById(subastaId)).thenReturn(true);
        when(pujaAutomaticaRepository.findBySubastaIdAndJugadorId(subastaId, jugador))
                .thenReturn(Optional.empty());

        servicio.desactivar(subastaId, jugador);

        verify(pujaAutomaticaRepository, never()).save(any());
    }

    @Test
    void desactivarSobreUnaSubastaInexistenteFalla() {
        UUID inexistente = UUID.randomUUID();
        when(subastaRepository.existsById(inexistente)).thenReturn(false);

        assertThrows(SubastaNoEncontradaException.class,
                () -> servicio.desactivar(inexistente, UUID.randomUUID()));
    }
}
