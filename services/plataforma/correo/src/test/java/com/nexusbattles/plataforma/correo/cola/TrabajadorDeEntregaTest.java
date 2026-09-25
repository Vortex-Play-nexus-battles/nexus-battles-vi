package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import com.nexusbattles.plataforma.correo.envio.ComposicionDeCorreo;
import com.nexusbattles.plataforma.correo.envio.ConfiguracionDeCorreo;
import com.nexusbattles.plataforma.correo.envio.DestinoDeEntrega;
import com.nexusbattles.plataforma.correo.envio.EnlacesDeCorreo;
import com.nexusbattles.plataforma.correo.envio.EnviadorCorreoService;
import com.nexusbattles.plataforma.correo.envio.ResultadoDeEntrega;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El trabajador de la cola, con la base y el SMTP de mentira: que estado
 * escribe tras cada intento, cuando no envia, y como recupera lo que un
 * reinicio dejo a medias. Contra PostgreSQL y Mailpit de verdad, en
 * {@code CorreoDurableIT}.
 */
class TrabajadorDeEntregaTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final Map<String, Object> DATOS_RECUPERACION =
            Map.of("apodo", "ElGuerrero", "codigo", "482915", "minutosVigencia", 15);

    private RepositorioDeEnvios repositorio;
    private EnviadorCorreoService enviador;
    private MetricasDeCorreo metricas;
    private TrabajadorDeEntrega trabajador;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioDeEnvios.class);
        enviador = mock(EnviadorCorreoService.class);
        metricas = mock(MetricasDeCorreo.class);
        ConfiguracionDeEntrega configuracion = ConfiguracionDeEntrega.porOmision();
        trabajador = new TrabajadorDeEntrega(
                repositorio,
                enviador,
                new ComposicionDeCorreo(new EnlacesDeCorreo(
                        new ConfiguracionDeCorreo(null, null, "https://nexus.example.com"))),
                new MaquinaDeEstados(new PoliticaDeReintentos(configuracion.esperas(), configuracion.maxIntentos())),
                configuracion,
                metricas,
                TransactionOperations.withoutTransaction(),
                Clock.fixed(AHORA, ZoneOffset.UTC));
        when(repositorio.renovarReclamo(any(), anyInt(), any())).thenReturn(true);
        when(repositorio.registrarResultado(any(), anyInt(), any(), any())).thenReturn(true);
        when(repositorio.tomarAtascados(any(), anyInt())).thenReturn(List.of());
    }

    @AfterEach
    void limpiarTraza() {
        MDC.remove(FiltroDeTraza.CLAVE_MDC);
    }

    private static EnvioEnCola envio(String plantilla, Map<String, Object> datos, int intentos) {
        return new EnvioEnCola(UUID.randomUUID(), plantilla, "jugador@ejemplo.com", "Asunto", datos, intentos,
                "traza-del-registro", AHORA.minusSeconds(5));
    }

    private void enCola(EnvioEnCola... envios) {
        when(repositorio.reclamar(10, AHORA)).thenReturn(List.of(envios));
    }

    private void elServidorResponde(ResultadoDeEntrega resultado) {
        when(enviador.enviar(anyString(), anyString(), anyString(), anyMap())).thenReturn(resultado);
    }

    private CambioDeEstado cambioAnotado(EnvioEnCola envio) {
        ArgumentCaptor<CambioDeEstado> cambio = ArgumentCaptor.forClass(CambioDeEstado.class);
        verify(repositorio).registrarResultado(eq(envio.id()), eq(envio.intentos()), cambio.capture(), eq(AHORA));
        return cambio.getValue();
    }

    @Test
    void sinNadaVencidoLaRondaNoEnviaNada() {
        when(repositorio.reclamar(10, AHORA)).thenReturn(List.of());

        assertThat(trabajador.procesarRonda()).isZero();

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void unCorreoEntregadoQuedaEnviadoSinSuCodigoYSuma() {
        EnvioEnCola envio = envio("recuperacion-clave", DATOS_RECUPERACION, 1);
        enCola(envio);
        elServidorResponde(ResultadoDeEntrega.entregado(DestinoDeEntrega.PROVEEDOR, "<id@smtp>"));

        assertThat(trabajador.procesarRonda()).isEqualTo(1);

        CambioDeEstado cambio = cambioAnotado(envio);
        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.ENVIADO);
        assertThat(cambio.identificador()).isEqualTo("<id@smtp>");
        assertThat(cambio.datos()).doesNotContainKey("codigo");
        verify(metricas).registrar(EstadoDeEnvio.ENVIADO, "recuperacion-clave");
    }

    @Test
    void elCorreoSePintaConSuPlantillaYConElEnlaceArmadoAlEntregar() {
        EnvioEnCola envio = envio("recuperacion-clave", DATOS_RECUPERACION, 1);
        enCola(envio);
        elServidorResponde(ResultadoDeEntrega.entregado(DestinoDeEntrega.PROVEEDOR, "<id@smtp>"));

        trabajador.procesarRonda();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(enviador).enviar(eq("jugador@ejemplo.com"), eq("Asunto"), eq("email/recuperacion-clave"),
                variables.capture());
        assertThat(variables.getValue())
                .containsEntry("codigo", "482915")
                .containsEntry("enlace",
                        "https://nexus.example.com/restablecer#codigo=482915&correo=jugador%40ejemplo.com");
    }

    @Test
    void laEntregaSaleEnLaBitacoraConLaTrazaDeLaPeticionQueLaPidio() {
        MDC.put(FiltroDeTraza.CLAVE_MDC, "traza-de-la-ronda");
        EnvioEnCola envio = envio("bienvenida", Map.of("saludo", "Ana"), 1);
        enCola(envio);
        AtomicReference<String> trazaDuranteElEnvio = new AtomicReference<>();
        when(enviador.enviar(anyString(), anyString(), anyString(), anyMap())).thenAnswer(invocacion -> {
            trazaDuranteElEnvio.set(MDC.get(FiltroDeTraza.CLAVE_MDC));
            return ResultadoDeEntrega.entregado(DestinoDeEntrega.PROVEEDOR, "<id>");
        });

        trabajador.procesarRonda();

        assertThat(trazaDuranteElEnvio.get()).isEqualTo("traza-del-registro");
        assertThat(MDC.get(FiltroDeTraza.CLAVE_MDC)).as("y despues se restaura").isEqualTo("traza-de-la-ronda");
    }

    @Test
    void sinTrazaPreviaElMdcQuedaLimpio() {
        enCola(envio("bienvenida", Map.of("saludo", "Ana"), 1));
        elServidorResponde(ResultadoDeEntrega.entregado(DestinoDeEntrega.PROVEEDOR, "<id>"));

        trabajador.procesarRonda();

        assertThat(MDC.get(FiltroDeTraza.CLAVE_MDC)).isNull();
    }

    @Test
    void unFalloTransitorioVuelveALaColaConSuEsperaYSinPerderElCodigo() {
        EnvioEnCola envio = envio("recuperacion-clave", DATOS_RECUPERACION, 1);
        enCola(envio);
        elServidorResponde(ResultadoDeEntrega.fallido(false, "MailConnectException: Couldn't connect to host"));

        trabajador.procesarRonda();

        CambioDeEstado cambio = cambioAnotado(envio);
        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.ERROR_REINTENTABLE);
        assertThat(cambio.proximoIntento()).isEqualTo(AHORA.plus(Duration.ofSeconds(30)));
        assertThat(cambio.datos()).isNull();
        verify(metricas).registrar(EstadoDeEnvio.ERROR_REINTENTABLE, "recuperacion-clave");
    }

    @Test
    void unRechazoPermanenteQuedaFallidoAlPrimerIntento() {
        EnvioEnCola envio = envio("bienvenida", Map.of("saludo", "Ana"), 1);
        enCola(envio);
        elServidorResponde(ResultadoDeEntrega.fallido(true, "SendFailedException: 550 5.1.1 User unknown"));

        trabajador.procesarRonda();

        assertThat(cambioAnotado(envio).estado()).isEqualTo(EstadoDeEnvio.FALLIDO);
    }

    @Test
    void elOctavoFalloTransitorioAgotaLosIntentos() {
        EnvioEnCola envio = envio("recuperacion-clave", DATOS_RECUPERACION, 8);
        enCola(envio);
        elServidorResponde(ResultadoDeEntrega.fallido(false, "421 Service not available"));

        trabajador.procesarRonda();

        CambioDeEstado cambio = cambioAnotado(envio);
        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.FALLIDO);
        assertThat(cambio.datos()).doesNotContainKey("codigo");
    }

    @Test
    void unDesvioYUnaOmisionTambienSeAnotan() {
        EnvioEnCola desviado = envio("bienvenida", Map.of("saludo", "Ana"), 1);
        EnvioEnCola omitido = envio("bienvenida", Map.of("saludo", "Bea"), 1);
        enCola(desviado, omitido);
        when(enviador.enviar(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(ResultadoDeEntrega.entregado(DestinoDeEntrega.BUZON_DE_PRUEBAS, "<id>"))
                .thenReturn(ResultadoDeEntrega.omitido("dominio reservado"));

        trabajador.procesarRonda();

        assertThat(cambioAnotado(desviado).estado()).isEqualTo(EstadoDeEnvio.DESVIADO);
        assertThat(cambioAnotado(omitido).estado()).isEqualTo(EstadoDeEnvio.OMITIDO);
    }

    @Test
    void siOtroTrabajadorYaTieneLaFilaNoSeEnvia() {
        EnvioEnCola envio = envio("bienvenida", Map.of("saludo", "Ana"), 1);
        enCola(envio);
        when(repositorio.renovarReclamo(envio.id(), 1, AHORA)).thenReturn(false);

        trabajador.procesarRonda();

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), anyMap());
        verify(repositorio, never()).registrarResultado(any(), anyInt(), any(), any());
    }

    @Test
    void siElResultadoLlegaTardeNoSeCuentaDosVeces() {
        EnvioEnCola envio = envio("bienvenida", Map.of("saludo", "Ana"), 1);
        enCola(envio);
        elServidorResponde(ResultadoDeEntrega.entregado(DestinoDeEntrega.PROVEEDOR, "<id>"));
        when(repositorio.registrarResultado(any(), anyInt(), any(), any())).thenReturn(false);

        trabajador.procesarRonda();

        verify(metricas, never()).registrar(any(), anyString());
    }

    @Test
    void unFalloInesperadoDelServicioSeReintentaEnVezDePerderElCorreo() {
        EnvioEnCola envio = envio("bienvenida", Map.of("saludo", "Ana"), 1);
        enCola(envio);
        when(enviador.enviar(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("defecto del servicio"));

        trabajador.procesarRonda();

        CambioDeEstado cambio = cambioAnotado(envio);
        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.ERROR_REINTENTABLE);
        assertThat(cambio.error()).contains("defecto del servicio");
    }

    @Test
    void unaPlantillaDeOtraVersionDelServicioFallaSinIntentarEnviar() {
        EnvioEnCola envio = envio("plantilla-del-futuro", Map.of("x", 1), 1);
        enCola(envio);

        trabajador.procesarRonda();

        verify(enviador, never()).enviar(anyString(), anyString(), anyString(), anyMap());
        CambioDeEstado cambio = cambioAnotado(envio);
        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.FALLIDO);
        assertThat(cambio.error()).contains("plantilla-del-futuro");
        assertThat(cambio.datos()).isEmpty();
    }

    @Test
    void losAtascadosVuelvenALaColaComoFalloTransitorio() {
        EnvioEnCola atascado = envio("recuperacion-clave", DATOS_RECUPERACION, 2);
        when(repositorio.tomarAtascados(AHORA.minus(Duration.ofMinutes(5)), 10)).thenReturn(List.of(atascado));

        assertThat(trabajador.recuperarAtascados()).isEqualTo(1);

        CambioDeEstado cambio = cambioAnotado(atascado);
        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.ERROR_REINTENTABLE);
        assertThat(cambio.proximoIntento()).as("tras el 2.o intento se espera 1 min")
                .isEqualTo(AHORA.plus(Duration.ofMinutes(1)));
        assertThat(cambio.error()).isEqualTo(TrabajadorDeEntrega.MOTIVO_INTERRUMPIDA);
        verify(metricas).registrar(EstadoDeEnvio.ERROR_REINTENTABLE, "recuperacion-clave");
    }

    @Test
    void unAtascadoQueYaAgotoSusIntentosQuedaFallidoSinCodigo() {
        EnvioEnCola atascado = envio("recuperacion-clave", DATOS_RECUPERACION, 8);
        when(repositorio.tomarAtascados(any(), anyInt())).thenReturn(List.of(atascado));

        trabajador.recuperarAtascados();

        CambioDeEstado cambio = cambioAnotado(atascado);
        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.FALLIDO);
        assertThat(cambio.datos()).doesNotContainKey("codigo");
    }

    @Test
    void cadaRondaEmpiezaRecuperandoLosAtascados() {
        when(repositorio.reclamar(anyInt(), any())).thenReturn(List.of());

        trabajador.procesarRonda();

        verify(repositorio).tomarAtascados(AHORA.minus(Duration.ofMinutes(5)), 10);
    }

    @Test
    void laPurgaBorraLoTerminadoHaceMasDeTreintaDias() {
        when(repositorio.purgarTerminadosAntesDe(AHORA.minus(Duration.ofDays(30)))).thenReturn(3);

        assertThat(trabajador.purgar()).isEqualTo(3);
    }

    @Test
    void unaPurgaSinNadaQueBorrarNoHaceNadaMas() {
        when(repositorio.purgarTerminadosAntesDe(any())).thenReturn(0);

        assertThat(trabajador.purgar()).isZero();
    }
}
