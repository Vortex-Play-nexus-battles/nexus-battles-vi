package com.nexusbattles.ms_identidad.onboarding.service;

import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteCreditos;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteInventario;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteInventario.Elemento;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteInventario.Equipamiento;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoPaso;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingJugador;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingPaso;
import com.nexusbattles.ms_identidad.onboarding.model.PasoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingJugadorRepository;
import com.nexusbattles.ms_identidad.onboarding.repository.OnboardingPasoRepository;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido.Causa;
import com.nexusbattles.ms_identidad.onboarding.service.PoliticaInicial.CreditosIniciales;
import com.nexusbattles.ms_identidad.onboarding.service.PoliticaInicial.Fuente;
import com.nexusbattles.ms_identidad.onboarding.service.PoliticaInicial.KitInicial;
import com.nexusbattles.ms_identidad.onboarding.service.PoliticaInicial.ProductoDelKit;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Procesador del alta: pasos, idempotencia y reintentos (R17)")
class ProcesadorOnboardingTest {

    private static final UUID UID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
    private static final Instant AHORA = Instant.parse("2026-09-24T12:00:00Z");
    private static final LocalDateTime HOY = LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final KitInicial KIT = new KitInicial(
            new ProductoDelKit("p-heroe", "Guerrero Tanque", "HEROE", null),
            List.of(new ProductoDelKit("p-espada", "Espada", "ARMA", null)),
            Fuente.RESPALDO_DEV);

    private OnboardingJugadorRepository jugadores;
    private OnboardingPasoRepository pasos;
    private PoliticaInicial politica;
    private ClienteCreditos creditos;
    private ClienteInventario inventario;
    private AuditoriaDeCuenta auditoria;
    private ProcesadorOnboarding procesador;

    private final Map<PasoOnboarding, OnboardingPaso> guardados = new EnumMap<>(PasoOnboarding.class);
    private final AtomicReference<OnboardingJugador> alta = new AtomicReference<>();

    @BeforeEach
    void preparar() {
        jugadores = mock(OnboardingJugadorRepository.class);
        pasos = mock(OnboardingPasoRepository.class);
        politica = mock(PoliticaInicial.class);
        creditos = mock(ClienteCreditos.class);
        inventario = mock(ClienteInventario.class);
        auditoria = mock(AuditoriaDeCuenta.class);
        procesador = new ProcesadorOnboarding(jugadores, pasos, politica, creditos, inventario, auditoria,
                mock(PlatformTransactionManager.class), Clock.fixed(AHORA, ZoneOffset.UTC));

        when(jugadores.reclamar(eq(UID), eq(EstadoOnboarding.EN_PROCESO), any(), any(), any())).thenReturn(1);
        when(jugadores.findById(UID)).thenAnswer(invocacion -> Optional.ofNullable(alta.get()));
        when(jugadores.save(any(OnboardingJugador.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(pasos.findByUsuarioUid(UID)).thenAnswer(invocacion -> new ArrayList<>(guardados.values()));
        when(pasos.save(any(OnboardingPaso.class))).thenAnswer(invocacion -> {
            OnboardingPaso paso = invocacion.getArgument(0);
            guardados.put(paso.getPaso(), paso);
            return paso;
        });

        alta.set(altaEnProceso(1));
        guardados.put(PasoOnboarding.PERFIL, new OnboardingPaso(UID, PasoOnboarding.PERFIL, EstadoPaso.HECHO, "perfil", HOY));
        for (PasoOnboarding paso : List.of(PasoOnboarding.CREDITOS, PasoOnboarding.HEROE, PasoOnboarding.EQUIPO)) {
            guardados.put(paso, new OnboardingPaso(UID, paso, EstadoPaso.PENDIENTE, null, HOY));
        }

        when(politica.creditos()).thenReturn(new CreditosIniciales(500, Fuente.RESPALDO_DEV));
        when(politica.kit()).thenReturn(KIT);
        when(creditos.acreditar(UID, 500, "bono-registro-v1-" + UID)).thenReturn(
                new ClienteCreditos.Acreditacion("TX-ACR-0000000A", "bono-registro-v1-" + UID, "APLICADO",
                        new BigDecimal("500.00"), new BigDecimal("500.00")));
        when(inventario.elementosDe(UID)).thenReturn(List.of());
        when(inventario.crear(UID, "p-heroe", "HEROE", "Guerrero Tanque", null)).thenReturn(elemento("h1", "p-heroe", "HEROE"));
        when(inventario.crear(UID, "p-espada", "ARMA", "Espada", null)).thenReturn(elemento("a1", "p-espada", "ARMA"));
        when(inventario.equipamientoDe(UID, "h1")).thenReturn(new Equipamiento("h1", List.of(), Map.of(), List.of()));
    }

    /** Como queda tras {@code reclamar}: en proceso y con el intento contado. */
    private static OnboardingJugador altaEnProceso(int intentos) {
        OnboardingJugador enProceso = new OnboardingJugador(UID, 1, "4bf92f3577b34da6a3ce929d0e0e4736", HOY.minusMinutes(1));
        ReflectionTestUtils.setField(enProceso, "estado", EstadoOnboarding.EN_PROCESO);
        ReflectionTestUtils.setField(enProceso, "intentos", intentos);
        ReflectionTestUtils.setField(enProceso, "enProcesoHasta", HOY.plusMinutes(2));
        return enProceso;
    }

    private static Elemento elemento(String id, String producto, String tipo) {
        return new Elemento(id, producto, tipo, tipo, null, true, null);
    }

    @Test
    @DisplayName("jugador nuevo: creditos, heroe y equipo contra sus duenos; alta COMPLETA y auditada")
    void completaLosTresPasos() {
        ProcesadorOnboarding.Resultado resultado = procesador.procesar(UID);

        assertThat(resultado).isEqualTo(ProcesadorOnboarding.Resultado.COMPLETO);
        assertThat(alta.get().getEstado()).isEqualTo(EstadoOnboarding.COMPLETO);
        assertThat(alta.get().getCompletadoEn()).isEqualTo(HOY);
        assertThat(guardados.values()).allMatch(OnboardingPaso::hecho);
        assertThat(guardados.get(PasoOnboarding.CREDITOS).getDetalle())
                .isEqualTo("transaccion=TX-ACR-0000000A;monto=500;fuente=RESPALDO_DEV;ref=bono-registro-v1-" + UID);
        assertThat(guardados.get(PasoOnboarding.HEROE).getDetalle()).isEqualTo("heroe=h1;producto=p-heroe");
        assertThat(guardados.get(PasoOnboarding.EQUIPO).getDetalle()).isEqualTo("heroe=h1;equipados=a1");
        verify(inventario).equipar(UID, "h1", "a1");
        verify(auditoria).altaCompletada(eq(UID), contains("creditos=500;fuente=RESPALDO_DEV;heroe=h1;equipados=a1"));
        verify(auditoria, never()).altaFallida(any(), any());
        assertThat(Traza.actual()).as("la traza del hilo se limpia al terminar").isEmpty();
    }

    @Test
    @DisplayName("si otro trabajador tiene el turno, no se toca nada")
    void noTomado() {
        when(jugadores.reclamar(eq(UID), eq(EstadoOnboarding.EN_PROCESO), any(), any(), any())).thenReturn(0);

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.NO_TOMADO);

        verifyNoInteractions(politica, creditos, inventario, auditoria);
    }

    @Test
    @DisplayName("defensivo: turno tomado pero el alta no aparece")
    void altaDesaparecida() {
        alta.set(null);
        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.NO_TOMADO);
        verifyNoInteractions(creditos, inventario);
    }

    @Test
    @DisplayName("ms-finanzas caido: heroe y equipo se hacen igual; el alta queda reintentable en 15 s y se audita una vez")
    void finanzasCaida() {
        when(creditos.acreditar(eq(UID), anyLong(), anyString()))
                .thenThrow(new PasoFallido(Causa.SERVICIO_NO_DISPONIBLE, "ms-finanzas acreditar sin respuesta"));

        ProcesadorOnboarding.Resultado resultado = procesador.procesar(UID);

        assertThat(resultado).isEqualTo(ProcesadorOnboarding.Resultado.APLAZADO);
        assertThat(alta.get().getEstado()).isEqualTo(EstadoOnboarding.ERROR_REINTENTABLE);
        assertThat(alta.get().getSiguienteIntento()).isEqualTo(HOY.plusSeconds(15));
        assertThat(alta.get().getUltimoError()).startsWith("CREDITOS=SERVICIO_NO_DISPONIBLE: ");
        OnboardingPaso credito = guardados.get(PasoOnboarding.CREDITOS);
        assertThat(credito.getEstado()).isEqualTo(EstadoPaso.ERROR);
        assertThat(credito.getIntentos()).isEqualTo(1);
        assertThat(guardados.get(PasoOnboarding.HEROE).hecho()).isTrue();
        assertThat(guardados.get(PasoOnboarding.EQUIPO).hecho()).isTrue();
        verify(auditoria).altaFallida(UID, "pendientes=CREDITOS");
        verify(auditoria, never()).altaCompletada(any(), any());
    }

    @Test
    @DisplayName("inventario caido: los creditos se acreditan; el equipo espera al heroe")
    void inventarioCaido() {
        when(inventario.elementosDe(UID))
                .thenThrow(new PasoFallido(Causa.SERVICIO_NO_DISPONIBLE, "inventario sin respuesta"));

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.APLAZADO);

        assertThat(guardados.get(PasoOnboarding.CREDITOS).hecho()).isTrue();
        assertThat(guardados.get(PasoOnboarding.HEROE).getUltimoError()).startsWith("SERVICIO_NO_DISPONIBLE");
        assertThat(guardados.get(PasoOnboarding.EQUIPO).getUltimoError()).startsWith("DEPENDE_DE_OTRO_PASO");
        verify(inventario, never()).crear(any(), any(), any(), any(), any());
        verify(auditoria).altaFallida(UID, "pendientes=HEROE,EQUIPO");
    }

    @Test
    @DisplayName("reintento tras un fallo a medias: no repite creditos ni crea duplicados; adopta lo que ya existe")
    void reintentoIdempotente() {
        alta.set(altaEnProceso(2));
        guardados.get(PasoOnboarding.CREDITOS).marcarHecho("transaccion=TX-ACR-0000000A;monto=500;fuente=PARAMETRO", HOY);
        // Un intento anterior creo el heroe y la espada y los equipo, y murio antes de anotarlo.
        when(inventario.elementosDe(UID)).thenReturn(List.of(
                elemento("h1", "p-heroe", "HEROE"),
                new Elemento("a1", "p-espada", "ARMA", "Espada", null, false, null)));
        when(inventario.equipamientoDe(UID, "h1")).thenReturn(new Equipamiento("h1", List.of("a1"), Map.of(), List.of()));

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.COMPLETO);

        verify(creditos, never()).acreditar(any(), anyLong(), any());
        verify(inventario, never()).crear(any(), any(), any(), any(), any());
        verify(inventario, never()).equipar(any(), any(), any());
        assertThat(guardados.get(PasoOnboarding.EQUIPO).getDetalle()).isEqualTo("heroe=h1;equipados=a1");
        verify(auditoria).altaCompletada(eq(UID), contains("fuente=PARAMETRO"));
    }

    @Test
    @DisplayName("un elemento en subasta no se adopta: se crea uno nuevo")
    void noAdoptaLoQueEstaEnSubasta() {
        when(inventario.elementosDe(UID)).thenReturn(List.of(
                new Elemento("vendida", "p-espada", "ARMA", "Espada", null, false, "subasta-1")));

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.COMPLETO);

        verify(inventario).crear(UID, "p-espada", "ARMA", "Espada", null);
        verify(inventario).equipar(UID, "h1", "a1");
    }

    @Test
    @DisplayName("la armadura del kit se crea con su parte")
    void armaduraConParte() {
        when(politica.kit()).thenReturn(new KitInicial(KIT.heroe(),
                List.of(new ProductoDelKit("p-casco", "Casco", "ARMADURA", "CASCO")), Fuente.PARAMETRO));
        when(inventario.crear(UID, "p-casco", "ARMADURA", "Casco", "CASCO"))
                .thenReturn(new Elemento("c1", "p-casco", "ARMADURA", "Casco", "CASCO", true, null));

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.COMPLETO);

        verify(inventario).equipar(UID, "h1", "c1");
    }

    @Test
    @DisplayName("un error inesperado de un paso se anota como rechazo y no rompe el resto")
    void inesperado() {
        when(inventario.crear(UID, "p-heroe", "HEROE", "Guerrero Tanque", null))
                .thenThrow(new IllegalStateException("bug"));

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.APLAZADO);

        assertThat(guardados.get(PasoOnboarding.HEROE).getUltimoError())
                .startsWith("RECHAZADO: error inesperado: IllegalStateException: bug");
        assertThat(guardados.get(PasoOnboarding.CREDITOS).hecho()).isTrue();
    }

    @Test
    @DisplayName("el paso HEROE sin id anotado no deja equipar")
    void heroeSinId() {
        guardados.get(PasoOnboarding.CREDITOS).marcarHecho("monto=500", HOY);
        guardados.get(PasoOnboarding.HEROE).marcarHecho("producto=p-heroe", HOY);

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.APLAZADO);

        assertThat(guardados.get(PasoOnboarding.EQUIPO).getUltimoError()).contains("no dejo anotado el heroe");
    }

    @Test
    @DisplayName("un segundo aplazamiento no vuelve a auditar el fallo y espera el doble")
    void segundoAplazamiento() {
        alta.set(altaEnProceso(2));
        when(politica.creditos()).thenThrow(new PasoFallido(Causa.CONFIGURACION_INCOMPLETA, "sin valor"));

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.APLAZADO);

        assertThat(alta.get().getSiguienteIntento()).isEqualTo(HOY.plusSeconds(30));
        verify(auditoria, never()).altaFallida(any(), any());
    }

    @Test
    @DisplayName("si faltan filas de pasos (alta a medio crear), nacen pendientes y se completan")
    void faltanFilas() {
        guardados.remove(PasoOnboarding.CREDITOS);
        guardados.remove(PasoOnboarding.HEROE);
        guardados.remove(PasoOnboarding.EQUIPO);

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.COMPLETO);

        verify(pasos, times(3)).save(any(OnboardingPaso.class));
        assertThat(guardados).hasSize(4);
    }

    @Test
    @DisplayName("un monto sin eco de ms-finanzas se anota con el de la politica")
    void montoSinEco() {
        when(creditos.acreditar(UID, 500, "bono-registro-v1-" + UID)).thenReturn(
                new ClienteCreditos.Acreditacion("TX-ACR-0000000B", "r", "APLICADO", null, null));

        procesador.procesar(UID);

        assertThat(guardados.get(PasoOnboarding.CREDITOS).getDetalle()).contains("monto=500");
    }

    @Test
    @DisplayName("espera creciente con tope: 15 s, 30 s, 1 min... 10 min")
    void espera() {
        assertThat(ProcesadorOnboarding.espera(0)).isEqualTo(Duration.ofSeconds(15));
        assertThat(ProcesadorOnboarding.espera(1)).isEqualTo(Duration.ofSeconds(15));
        assertThat(ProcesadorOnboarding.espera(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(ProcesadorOnboarding.espera(3)).isEqualTo(Duration.ofMinutes(1));
        assertThat(ProcesadorOnboarding.espera(6)).isEqualTo(Duration.ofMinutes(8));
        assertThat(ProcesadorOnboarding.espera(7)).isEqualTo(Duration.ofMinutes(10));
        assertThat(ProcesadorOnboarding.espera(500)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("la clave de idempotencia es uid + version; el detalle se lee por clave")
    void claves() {
        assertThat(ProcesadorOnboarding.refIdDelBono(UID, 3)).isEqualTo("bono-registro-v3-" + UID);
        assertThat(ProcesadorOnboarding.valorDe("a=1; b = 2 ;c=", "b")).isEqualTo("2");
        assertThat(ProcesadorOnboarding.valorDe("a=1;c=", "c")).isNull();
        assertThat(ProcesadorOnboarding.valorDe("sin-igual", "sin-igual")).isNull();
        assertThat(ProcesadorOnboarding.valorDe(null, "a")).isNull();
    }

    @Test
    @DisplayName("el paso anotado como hecho no se vuelve a ejecutar aunque la politica fallara ahora")
    void hechoNoSeRepite() {
        guardados.get(PasoOnboarding.CREDITOS).marcarHecho("monto=500", HOY);
        when(politica.creditos()).thenThrow(new IllegalStateException("no deberia llamarse"));

        assertThat(procesador.procesar(UID)).isEqualTo(ProcesadorOnboarding.Resultado.COMPLETO);

        verify(politica, never()).creditos();
        verify(creditos, never()).acreditar(any(), anyLong(), isNull());
    }
}
