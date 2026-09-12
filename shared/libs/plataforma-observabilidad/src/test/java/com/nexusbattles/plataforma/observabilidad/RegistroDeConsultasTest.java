package com.nexusbattles.plataforma.observabilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * HU-REN-003 — el calculo del que sale la evidencia de RNF-REN-003.
 *
 * <p>El percentil entra siempre como parametro: cual se usa lo aprueba el
 * Product Owner y aqui no se elige ninguno.
 */
class RegistroDeConsultasTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T10:00:00Z");
    private static final ObjetivoDeLatencia P95 = new ObjetivoDeLatencia(500, 95);

    private static final String BUSCAR_TERMINO =
            "select t.id from terminos_prohibidos t where upper(t.termino) = upper(?)";
    private static final String BANDEJA =
            "select n.* from notificaciones n where n.usuario_id = ? order by n.creada_en desc";

    private final RegistroDeConsultas registro = new RegistroDeConsultas("moderacion-sanciones", 500);

    private void medir(String sentencia, long... duraciones) {
        for (long duracion : duraciones) {
            registro.registrar(new MuestraDeConsulta(
                    "moderacion-sanciones", sentencia, duracion, AHORA, false));
        }
    }

    @Test
    void unInformeSinConsultasNoCuentaComoCumplido() {
        // Un servicio cuyas consultas nunca se midieron no ha demostrado nada
        // sobre sus indices.
        InformeDeConsultas informe = registro.informe(P95, 10);

        assertThat(informe.sinDatos()).isTrue();
        assertThat(informe.cumple()).isFalse();
        assertThat(informe.sentenciasMasLentas()).isEmpty();
    }

    @Test
    void registraCadaConsultaConSuDuracion() {
        medir(BUSCAR_TERMINO, 3, 4, 5);

        assertThat(registro.cuantasMuestras()).isEqualTo(3);
        assertThat(registro.muestras())
                .extracting(MuestraDeConsulta::duracionMs)
                .containsExactly(3L, 4L, 5L);
    }

    @Test
    void elPercentilSaleDeTodasLasConsultasMedidas() {
        for (long ms = 1; ms <= 100; ms++) {
            medir(BUSCAR_TERMINO, ms);
        }

        InformeDeConsultas informe = registro.informe(P95, 10);

        assertThat(informe.percentilMs()).isEqualTo(95);
        assertThat(informe.maximoMs()).isEqualTo(100);
        assertThat(informe.cumple()).isTrue();
    }

    @Test
    void agrupaPorSentenciaYPoneLasMasLentasPrimero() {
        // CA-01 pide saber si las busquedas cumplen; CA-03, cual optimizar.
        // Sin agrupar por sentencia no se responde la segunda.
        medir(BANDEJA, 2, 3);
        medir(BUSCAR_TERMINO, 900);

        List<InformeDeConsultas.Sentencia> sentencias = registro.informe(P95, 10).sentenciasMasLentas();

        assertThat(sentencias).hasSize(2);
        assertThat(sentencias.get(0).sentencia()).isEqualTo(BUSCAR_TERMINO);
        assertThat(sentencias.get(0).percentilMs()).isEqualTo(900);
        assertThat(sentencias.get(1).muestras()).isEqualTo(2);
    }

    @Test
    void unaConsultaQueSuperaElUmbralQuedaMarcadaComoLenta() {
        // CA-03: «cuando una consulta supera el umbral, el sistema la marca en
        // el registro de consultas lentas».
        medir(BANDEJA, 10);
        medir(BUSCAR_TERMINO, 640);

        assertThat(registro.totalLentas()).isEqualTo(1);
        assertThat(registro.consultasLentas())
                .singleElement()
                .extracting(MuestraDeConsulta::sentencia)
                .isEqualTo(BUSCAR_TERMINO);
    }

    @Test
    void laConsultaJustoEnElUmbralNoSeMarca() {
        medir(BUSCAR_TERMINO, 500);

        assertThat(registro.totalLentas()).isZero();
        assertThat(registro.consultasLentas()).isEmpty();
    }

    @Test
    void unaRachaDeConsultasRapidasNoBorraLasLentasYaMarcadas() {
        // Por esto hay dos ventanas y no una: con una sola, las rapidas
        // expulsarian justo las lentas, que son las unicas que hay que mirar.
        RegistroDeConsultas acotado = new RegistroDeConsultas("s", 500, 3, 10);
        acotado.registrar(new MuestraDeConsulta("s", BUSCAR_TERMINO, 900, AHORA, false));
        for (int i = 0; i < 5; i++) {
            acotado.registrar(new MuestraDeConsulta("s", BANDEJA, 1, AHORA, false));
        }

        assertThat(acotado.cuantasMuestras()).isEqualTo(3);
        assertThat(acotado.consultasLentas()).hasSize(1);
        assertThat(acotado.totalLentas()).isEqualTo(1);
    }

    @Test
    void elTotalDeLentasCuentaTodasAunqueSoloSeRetenganAlgunas() {
        RegistroDeConsultas acotado = new RegistroDeConsultas("s", 500, 1000, 2);
        for (int i = 0; i < 5; i++) {
            acotado.registrar(new MuestraDeConsulta("s", BUSCAR_TERMINO, 900, AHORA, false));
        }

        assertThat(acotado.consultasLentas()).hasSize(2);
        assertThat(acotado.totalLentas()).isEqualTo(5);
    }

    @Test
    void lasConsultasLentasSalenDeLaMasRecienteALaMasVieja() {
        registro.registrar(new MuestraDeConsulta("s", "select 1", 600, AHORA, false));
        registro.registrar(new MuestraDeConsulta("s", "select 2", 700, AHORA, false));

        assertThat(registro.consultasLentas())
                .extracting(MuestraDeConsulta::sentencia)
                .containsExactly("select 2", "select 1");
    }

    @Test
    void laSentenciaSeNormalizaParaQueElMismoSqlNoCuenteDosVeces() {
        // Hibernate genera el SQL con saltos de linea y sangrias; sin
        // normalizar, la misma consulta formateada distinto seria dos
        // sentencias y ninguna tendria muestras suficientes.
        medir("select  a\n  from   b", 5);
        medir("select a from b", 6);

        assertThat(registro.informe(P95, 10).sentenciasMasLentas()).hasSize(1);
        assertThat(registro.muestras().get(0).sentencia()).isEqualTo("select a from b");
    }

    @Test
    void unaSentenciaEnormeSeRecortaParaNoInflarElInforme() {
        String larga = "select " + "x,".repeat(600);
        medir(larga, 5);

        assertThat(registro.muestras().get(0).sentencia())
                .hasSizeLessThanOrEqualTo(MuestraDeConsulta.LARGO_MAXIMO + 2)
                .endsWith("…");
    }

    @Test
    void unaSentenciaDesconocidaNoRompeElRegistro() {
        registro.registrar(new MuestraDeConsulta("s", null, 5, AHORA, false));

        assertThat(registro.muestras().get(0).sentencia()).isEqualTo("(sentencia desconocida)");
    }

    @Test
    void unaConsultaQueFallaTambienSeMide() {
        // Un tiempo de espera agotado es una consulta lenta, y es justo la que
        // interesa ver en el registro.
        registro.registrar(new MuestraDeConsulta("s", BUSCAR_TERMINO, 3000, AHORA, true));

        assertThat(registro.muestras().get(0).fallo()).isTrue();
        assertThat(registro.consultasLentas()).hasSize(1);
    }

    @Test
    void unaConfiguracionInvalidaEsUnError() {
        assertThatThrownBy(() -> new RegistroDeConsultas("s", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistroDeConsultas("s", 500, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MuestraDeConsulta("s", "select 1", -1, AHORA, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vaciarDejaElRegistroComoAlArrancar() {
        medir(BUSCAR_TERMINO, 900);
        registro.vaciar();

        assertThat(registro.cuantasMuestras()).isZero();
        assertThat(registro.totalLentas()).isZero();
        assertThat(registro.consultasLentas()).isEmpty();
    }
}
