package com.nexusbattles.plataforma.observabilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * HU-REN-001 — el calculo del que sale la evidencia de RNF-REN-001.
 *
 * <p>El percentil entra siempre como parametro: cual se usa para evaluar el
 * requisito lo aprueba el Product Owner (CA-03) y aqui no se elige ninguno.
 */
class RegistroDeLatenciaTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T10:00:00Z");
    private static final ObjetivoDeLatencia P95 = new ObjetivoDeLatencia(500, 95);
    private static final ObjetivoDeLatencia P99 = new ObjetivoDeLatencia(500, 99);

    private final RegistroDeLatencia registro = new RegistroDeLatencia("salas-partidas");

    private void registrarMuestras(long... duraciones) {
        for (long duracion : duraciones) {
            registro.registrar(new MuestraDeLatencia(
                    "salas-partidas", "POST", "/api/v1/salas", 200, duracion, AHORA));
        }
    }

    @Test
    void unInformeSinMuestrasNoCuentaComoCumplido() {
        // No medir no es lo mismo que cumplir: darlo por bueno dejaria pasar un
        // servicio que nunca llego a instrumentarse.
        InformeDeLatencia informe = registro.informe(P95, 5);

        assertThat(informe.sinDatos()).isTrue();
        assertThat(informe.muestras()).isZero();
        assertThat(informe.cumple()).isFalse();
        assertThat(informe.operacionesMasLentas()).isEmpty();
    }

    @Test
    void registraLaDuracionDeCadaPeticion() {
        registrarMuestras(120, 340);

        assertThat(registro.cuantasMuestras()).isEqualTo(2);
        assertThat(registro.muestras())
                .extracting(MuestraDeLatencia::duracionMs)
                .containsExactly(120L, 340L);
    }

    @Test
    void elPercentil95TomaElValorDeRangoMasCercano() {
        // 100 muestras de 1 a 100 ms: el p95 es 95 ms, un valor realmente medido.
        for (long ms = 1; ms <= 100; ms++) {
            registrarMuestras(ms);
        }

        assertThat(registro.informe(P95, 5).percentilMs()).isEqualTo(95);
    }

    @Test
    void elPercentil99EsMasExigenteQueElPercentil95() {
        for (long ms = 1; ms <= 100; ms++) {
            registrarMuestras(ms);
        }

        assertThat(registro.informe(P99, 5).percentilMs()).isEqualTo(99);
        assertThat(registro.informe(P99, 5).percentilMs())
                .isGreaterThan(registro.informe(P95, 5).percentilMs());
    }

    @Test
    void cambiarDePercentilNoExigeRecompilar() {
        // CA-03: el dia que el PO apruebe p95 o p99, cambiar el valor de
        // LATENCIA_PERCENTIL basta. Aqui se demuestra que el mismo registro
        // responde a cualquiera de los dos.
        for (long ms = 1; ms <= 100; ms++) {
            registrarMuestras(ms);
        }

        assertThat(registro.informe(new ObjetivoDeLatencia(500, 50), 5).percentilMs()).isEqualTo(50);
        assertThat(registro.informe(new ObjetivoDeLatencia(500, 90), 5).percentilMs()).isEqualTo(90);
    }

    @Test
    void cumpleCuandoElPercentilAcordadoQuedaBajoElObjetivo() {
        registrarMuestras(100, 150, 200, 250, 300);

        InformeDeLatencia informe = registro.informe(P95, 5);

        assertThat(informe.percentilMs()).isEqualTo(300);
        assertThat(informe.maximoMs()).isEqualTo(300);
        assertThat(informe.cumple()).isTrue();
    }

    @Test
    void noCumpleCuandoElPercentilAcordadoSuperaLos500Ms() {
        // El objetivo de RNF-REN-001 es innegociable: por encima, no cumple.
        registrarMuestras(100, 200, 300, 400, 900);

        InformeDeLatencia informe = registro.informe(P95, 5);

        assertThat(informe.percentilMs()).isEqualTo(900);
        assertThat(informe.cumple()).isFalse();
        assertThat(informe.objetivo().objetivoMs()).isEqualTo(500);
    }

    @Test
    void unaPeticionLentaAisladaNoTumbaElPercentilPeroSiElMaximo() {
        // 99 peticiones rapidas y una lenta: el p95 sigue en verde y el maximo
        // deja constancia de la lenta. Es la razon de reportar los dos.
        for (int i = 0; i < 99; i++) {
            registrarMuestras(50);
        }
        registrarMuestras(4000);

        InformeDeLatencia informe = registro.informe(P95, 5);

        assertThat(informe.percentilMs()).isEqualTo(50);
        assertThat(informe.maximoMs()).isEqualTo(4000);
        assertThat(informe.cumple()).isTrue();
    }

    @Test
    void agrupaPorOperacionYPoneLasMasLentasPrimero() {
        registro.registrar(new MuestraDeLatencia("s", "GET", "/api/v1/salas", 200, 10, AHORA));
        registro.registrar(new MuestraDeLatencia("s", "GET", "/api/v1/salas", 200, 20, AHORA));
        registro.registrar(new MuestraDeLatencia("s", "POST", "/api/v1/salas", 201, 800, AHORA));

        List<InformeDeLatencia.Operacion> operaciones = registro.informe(P95, 5).operacionesMasLentas();

        assertThat(operaciones).hasSize(2);
        assertThat(operaciones.get(0).metodo()).isEqualTo("POST");
        assertThat(operaciones.get(0).percentilMs()).isEqualTo(800);
        assertThat(operaciones.get(1).metodo()).isEqualTo("GET");
        assertThat(operaciones.get(1).muestras()).isEqualTo(2);
    }

    @Test
    void limitaCuantasOperacionesSeIncluyenEnElInforme() {
        registro.registrar(new MuestraDeLatencia("s", "GET", "/a", 200, 10, AHORA));
        registro.registrar(new MuestraDeLatencia("s", "GET", "/b", 200, 20, AHORA));
        registro.registrar(new MuestraDeLatencia("s", "GET", "/c", 200, 30, AHORA));

        assertThat(registro.informe(P95, 2).operacionesMasLentas()).hasSize(2);
    }

    @Test
    void laVentanaEsAcotadaYDescartaLasMuestrasMasViejas() {
        // Guardar todo acabaria tumbando el servicio que se pretende medir.
        RegistroDeLatencia acotado = new RegistroDeLatencia("s", 3);
        for (long ms : new long[] {1, 2, 3, 4, 5}) {
            acotado.registrar(new MuestraDeLatencia("s", "GET", "/a", 200, ms, AHORA));
        }

        assertThat(acotado.cuantasMuestras()).isEqualTo(3);
        assertThat(acotado.muestras())
                .extracting(MuestraDeLatencia::duracionMs)
                .containsExactly(3L, 4L, 5L);
    }

    @Test
    void unaCapacidadInvalidaEsUnError() {
        assertThatThrownBy(() -> new RegistroDeLatencia("s", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaDuracionNegativaEsUnError() {
        assertThatThrownBy(() -> new MuestraDeLatencia("s", "GET", "/a", 200, -1, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unPercentilFueraDeRangoEsUnErrorQueSenalaAlProductOwner() {
        // Configuracion invalida: el mensaje tiene que decir donde se arregla,
        // porque el valor correcto no lo decide quien programa.
        assertThatThrownBy(() -> new ObjetivoDeLatencia(500, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LATENCIA_PERCENTIL");
        assertThatThrownBy(() -> new ObjetivoDeLatencia(500, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ObjetivoDeLatencia(0, 95))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elNombreDelPercentilEsLegibleParaElInforme() {
        assertThat(new ObjetivoDeLatencia(500, 95).nombre()).isEqualTo("p95");
        assertThat(new ObjetivoDeLatencia(500, 99).nombre()).isEqualTo("p99");
        assertThat(new ObjetivoDeLatencia(500, 99.9).nombre()).isEqualTo("p99.9");
    }

    @Test
    void unaMuestraConCodigoDeServidorSeMarcaComoFallo() {
        MuestraDeLatencia error = new MuestraDeLatencia("s", "GET", "/a", 500, 12, AHORA);
        MuestraDeLatencia clienteMal = new MuestraDeLatencia("s", "GET", "/a", 400, 12, AHORA);

        assertThat(error.fallo()).isTrue();
        assertThat(clienteMal.fallo()).isFalse();
    }
}
