package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CofreServiceTest {

    // Miércoles 16 de septiembre de 2026 — ISO week 2026-W38.
    private static final Instant AHORA = Instant.parse("2026-09-16T10:00:00Z");
    private static final String SEMANA_ESPERADA = "2026-W38";

    @Mock
    private ContadorSemanalCofreRepository contadorRepositorio;

    @Mock
    private CofreEntregadoRepository cofreRepositorio;

    private CofreService servicio;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        servicio = new CofreService(contadorRepositorio, cofreRepositorio, reloj);
        // lenient(): no todos los tests entregan cofre, así que el stub del
        // save no siempre se invoca; sin lenient, Mockito considera el stub
        // innecesario y falla el test.
        org.mockito.Mockito.lenient()
                .when(cofreRepositorio.save(any(CofreEntregado.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registrar_creditosBajoUmbral_actualizaContadorYNoEntregaCofre() {
        when(contadorRepositorio.findById(any())).thenReturn(Optional.empty());

        Optional<CofreEntregado> resultado = servicio.registrarCreditosGanados("uid-1", 5);

        assertThat(resultado).isEmpty();
        ArgumentCaptor<ContadorSemanalCofre> captor = ArgumentCaptor.forClass(ContadorSemanalCofre.class);
        verify(contadorRepositorio).save(captor.capture());
        assertThat(captor.getValue().getCreditosGanados()).isEqualTo(5);
        assertThat(captor.getValue().getCofresEntregados()).isZero();
        verify(cofreRepositorio, never()).save(any());
    }

    @Test
    void registrar_alcanzaUmbralConCuotaDisponible_entregaCofreYReseteaContador() {
        ContadorSemanalCofre previo = new ContadorSemanalCofre("uid-1", SEMANA_ESPERADA);
        previo.setCreditosGanados(18);
        previo.setCofresEntregados(0);
        when(contadorRepositorio.findById(new ContadorSemanalCofreId("uid-1", SEMANA_ESPERADA)))
                .thenReturn(Optional.of(previo));

        Optional<CofreEntregado> resultado = servicio.registrarCreditosGanados("uid-1", 4);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getUidJugador()).isEqualTo("uid-1");
        assertThat(resultado.get().getSemanaIso()).isEqualTo(SEMANA_ESPERADA);
        assertThat(resultado.get().getContenido()).isEqualTo(CofreService.CONTENIDO_PLACEHOLDER);
        assertThat(resultado.get().getEntregadoEn()).isEqualTo(AHORA);
        // Contador se resetea a 0 y cofresEntregados sube a 1.
        ArgumentCaptor<ContadorSemanalCofre> captor = ArgumentCaptor.forClass(ContadorSemanalCofre.class);
        verify(contadorRepositorio).save(captor.capture());
        assertThat(captor.getValue().getCreditosGanados()).isZero();
        assertThat(captor.getValue().getCofresEntregados()).isEqualTo(1);
    }

    @Test
    void registrar_yaTiene2CofresEnLaSemana_noEntregaTercero() {
        ContadorSemanalCofre previo = new ContadorSemanalCofre("uid-1", SEMANA_ESPERADA);
        previo.setCreditosGanados(19);
        previo.setCofresEntregados(2); // ya en el tope semanal
        when(contadorRepositorio.findById(any())).thenReturn(Optional.of(previo));

        Optional<CofreEntregado> resultado = servicio.registrarCreditosGanados("uid-1", 5);

        assertThat(resultado).isEmpty();
        ArgumentCaptor<ContadorSemanalCofre> captor = ArgumentCaptor.forClass(ContadorSemanalCofre.class);
        verify(contadorRepositorio).save(captor.capture());
        // El contador sigue sumando aunque no se entregue cofre (útil para
        // reportes; el tope solo afecta la entrega).
        assertThat(captor.getValue().getCreditosGanados()).isEqualTo(24);
        assertThat(captor.getValue().getCofresEntregados()).isEqualTo(2);
        verify(cofreRepositorio, never()).save(any());
    }

    @Test
    void registrar_segundoCofreDeLaSemana_incrementaContadorDeCofresA2() {
        ContadorSemanalCofre previo = new ContadorSemanalCofre("uid-1", SEMANA_ESPERADA);
        previo.setCreditosGanados(19);
        previo.setCofresEntregados(1); // ya se entregó uno esta semana
        when(contadorRepositorio.findById(any())).thenReturn(Optional.of(previo));

        Optional<CofreEntregado> resultado = servicio.registrarCreditosGanados("uid-1", 2);

        assertThat(resultado).isPresent();
        ArgumentCaptor<ContadorSemanalCofre> captor = ArgumentCaptor.forClass(ContadorSemanalCofre.class);
        verify(contadorRepositorio).save(captor.capture());
        assertThat(captor.getValue().getCofresEntregados()).isEqualTo(2);
    }

    @Test
    void semanaActual_devuelveFormatoIso() {
        // Miércoles 16/sep/2026 pertenece a la semana ISO 2026-W38.
        assertThat(servicio.semanaActual()).isEqualTo(SEMANA_ESPERADA);
    }

    @Test
    void registrar_delegaUidCorrectoAlBuscarContador() {
        when(contadorRepositorio.findById(eq(new ContadorSemanalCofreId("uid-especifico", SEMANA_ESPERADA))))
                .thenReturn(Optional.empty());

        servicio.registrarCreditosGanados("uid-especifico", 1);

        verify(contadorRepositorio).findById(new ContadorSemanalCofreId("uid-especifico", SEMANA_ESPERADA));
    }
}
