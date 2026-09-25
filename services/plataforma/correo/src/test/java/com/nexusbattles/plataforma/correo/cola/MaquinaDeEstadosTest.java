package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.plataforma.correo.envio.DestinoDeEntrega;
import com.nexusbattles.plataforma.correo.envio.ResultadoDeEntrega;
import com.nexusbattles.plataforma.correo.template.Plantilla;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La maquina de estados de la cola: del resultado de un intento al estado
 * siguiente, con su espera y con lo que se borra al terminar.
 */
class MaquinaDeEstadosTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final Optional<Plantilla> RECUPERACION = Optional.of(Plantilla.RECUPERACION_CLAVE);
    private static final Map<String, Object> DATOS_CON_CODIGO =
            Map.of("apodo", "ElGuerrero", "codigo", "482915", "minutosVigencia", 15);

    private final MaquinaDeEstados maquina =
            new MaquinaDeEstados(new PoliticaDeReintentos(ConfiguracionDeEntrega.ESPERAS_POR_OMISION, 8));

    @Test
    void loQueAceptaElProveedorQuedaEnviadoConSuIdentificadorYSinElCodigo() {
        CambioDeEstado cambio = maquina.tras(RECUPERACION, DATOS_CON_CODIGO, 1,
                ResultadoDeEntrega.entregado(DestinoDeEntrega.PROVEEDOR, "<abc@smtp>"), AHORA);

        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.ENVIADO);
        assertThat(cambio.destino()).isEqualTo(DestinoDeEntrega.PROVEEDOR);
        assertThat(cambio.identificador()).isEqualTo("<abc@smtp>");
        assertThat(cambio.enviadoEn()).isEqualTo(AHORA);
        assertThat(cambio.error()).isNull();
        assertThat(cambio.datos())
                .as("el codigo de un solo uso no sobrevive al envio")
                .doesNotContainKey("codigo")
                .containsEntry("apodo", "ElGuerrero");
    }

    @Test
    void loQueAceptaElBuzonDePruebasQuedaDesviado() {
        CambioDeEstado cambio = maquina.tras(RECUPERACION, DATOS_CON_CODIGO, 1,
                ResultadoDeEntrega.entregado(DestinoDeEntrega.BUZON_DE_PRUEBAS, ""), AHORA);

        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.DESVIADO);
        assertThat(cambio.destino()).isEqualTo(DestinoDeEntrega.BUZON_DE_PRUEBAS);
        assertThat(cambio.identificador()).as("sin Message-ID no se inventa uno").isNull();
        assertThat(cambio.datos()).doesNotContainKey("codigo");
    }

    @Test
    void unaOmisionTerminaConSuMotivoYSinDatosSensibles() {
        CambioDeEstado cambio = maquina.tras(RECUPERACION, DATOS_CON_CODIGO, 1,
                ResultadoDeEntrega.omitido("dominio reservado para pruebas"), AHORA);

        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.OMITIDO);
        assertThat(cambio.error()).isEqualTo("dominio reservado para pruebas");
        assertThat(cambio.datos()).doesNotContainKey("codigo");
        assertThat(cambio.enviadoEn()).isNull();
    }

    @Test
    void unFalloTransitorioVuelveALaColaConEsperaYConservaElCodigo() {
        CambioDeEstado cambio = maquina.tras(RECUPERACION, DATOS_CON_CODIGO, 1,
                ResultadoDeEntrega.fallido(false, "ConnectException: Connection refused"), AHORA);

        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.ERROR_REINTENTABLE);
        assertThat(cambio.proximoIntento()).isEqualTo(AHORA.plus(Duration.ofSeconds(30)));
        assertThat(cambio.error()).isEqualTo("ConnectException: Connection refused");
        assertThat(cambio.datos())
                .as("nulo = no se tocan: el reintento tiene que poder volver a pintar el codigo")
                .isNull();
        assertThat(cambio.destino()).isNull();
    }

    @Test
    void cadaReintentoEsperaMasQueElAnterior() {
        ResultadoDeEntrega caido = ResultadoDeEntrega.fallido(false, "421 Try again later");

        assertThat(maquina.tras(RECUPERACION, DATOS_CON_CODIGO, 3, caido, AHORA).proximoIntento())
                .isEqualTo(AHORA.plus(Duration.ofMinutes(2)));
        assertThat(maquina.tras(RECUPERACION, DATOS_CON_CODIGO, 7, caido, AHORA).proximoIntento())
                .isEqualTo(AHORA.plus(Duration.ofHours(1)));
    }

    @Test
    void unRechazoPermanenteNoSeReintenta() {
        // 550 del destinatario: el buzon no existe, esperar no lo va a crear.
        CambioDeEstado cambio = maquina.tras(RECUPERACION, DATOS_CON_CODIGO, 1,
                ResultadoDeEntrega.fallido(true, "SendFailedException: 550 5.1.1 User unknown"), AHORA);

        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.FALLIDO);
        assertThat(cambio.error()).contains("550");
        assertThat(cambio.datos()).doesNotContainKey("codigo");
    }

    @Test
    void alAgotarLosIntentosQuedaFallidoYLoDice() {
        CambioDeEstado cambio = maquina.tras(RECUPERACION, DATOS_CON_CODIGO, 8,
                ResultadoDeEntrega.fallido(false, "ConnectException: Connection refused"), AHORA);

        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.FALLIDO);
        assertThat(cambio.error()).startsWith("agotados 8 intentos").contains("Connection refused");
        assertThat(cambio.datos()).doesNotContainKey("codigo");
    }

    @Test
    void conUnaPlantillaDesconocidaNoSeGuardaNingunDato() {
        CambioDeEstado cambio = maquina.tras(Optional.empty(), DATOS_CON_CODIGO, 1,
                ResultadoDeEntrega.fallido(true, "plantilla desconocida"), AHORA);

        assertThat(cambio.estado()).isEqualTo(EstadoDeEnvio.FALLIDO);
        assertThat(cambio.datos()).as("sin saber que es sensible, no se conserva nada").isEmpty();
    }

    @Test
    void elMotivoSeGuardaSaneado() {
        CambioDeEstado cambio = maquina.tras(RECUPERACION, DATOS_CON_CODIGO, 1,
                ResultadoDeEntrega.fallido(false, "550 <victima@gmail.com>:\r\n rechazado"), AHORA);

        assertThat(cambio.error()).isEqualTo("550 <v***a@gmail.com>: rechazado");
    }

    @Test
    void losEstadosTerminalesSonLosCuatroDelContrato() {
        assertThat(EstadoDeEnvio.values())
                .filteredOn(EstadoDeEnvio::esTerminal)
                .containsExactlyInAnyOrder(
                        EstadoDeEnvio.ENVIADO, EstadoDeEnvio.DESVIADO, EstadoDeEnvio.OMITIDO, EstadoDeEnvio.FALLIDO);
    }
}
