package com.nexusbattles.plataforma.observabilidad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.Ordered;

/**
 * HU-REN-001, CA-01 — la instrumentacion se reparte sola.
 *
 * <p>Estas pruebas son la evidencia de que CA-01 se cumple <b>sin copiar el
 * filtro servicio por servicio</b>: un modulo que aplica
 * {@code nexus.spring-conventions} recibe esta biblioteca y la
 * autoconfiguracion le deja el filtro puesto sin que escriba una linea.
 */
class ObservabilidadDeLatenciaAutoConfigurationTest {

    private final WebApplicationContextRunner contexto = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilidadDeLatenciaAutoConfiguration.class));

    @Test
    void unServicioQueNoConfiguraNadaQuedaInstrumentado() {
        contexto.run(ambiente -> {
            assertThat(ambiente).hasSingleBean(FiltroDeLatencia.class);
            assertThat(ambiente).hasSingleBean(RegistroDeLatencia.class);
            assertThat(ambiente).hasSingleBean(PropiedadesDeLatencia.class);
        });
    }

    @Test
    void elFiltroSeRegistraCasiAlPrincipioDeLaCadena() {
        // Un Filter declarado como bean y sin orden queda el ultimo, y medir
        // desde ahi dejaria fuera el tiempo de los filtros de seguridad y de
        // conversion. RNF-REN-001 pide latencia extremo a extremo.
        contexto.run(ambiente -> assertThat(ambiente.getBean(FiltroDeLatencia.class).getOrder())
                .isLessThan(Ordered.LOWEST_PRECEDENCE)
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10));
    }

    @Test
    void unServicioQueNoConfiguraNadaMideYEvaluaAlP95DeAdr006() {
        // Un modulo que no toca su application.yml —el caso de dieciocho de
        // los veinte— mide y ademas puede evaluar. Antes de ADR-006 el
        // objetivo quedaba vacio y el informe respondia 409 para siempre.
        contexto.run(ambiente -> {
            assertThat(ambiente).hasSingleBean(FiltroDeLatencia.class);
            ObjetivoDeLatencia objetivo = ambiente.getBean(PropiedadesDeLatencia.class).objetivo();
            assertThat(objetivo.nombre()).isEqualTo("p95");
            assertThat(objetivo.objetivoMs()).isEqualTo(500);
        });
    }

    @Test
    void elPercentilSeSigueCambiandoPorVariableDeEntorno() {
        contexto.withPropertyValues("latencia.percentil=99", "latencia.objetivo-ms=500")
                .run(ambiente -> {
                    ObjetivoDeLatencia objetivo = ambiente.getBean(PropiedadesDeLatencia.class).objetivo();
                    assertThat(objetivo.nombre()).isEqualTo("p99");
                    assertThat(objetivo.objetivoMs()).isEqualTo(500);
                });
    }

    @Test
    void unaVariableDeEntornoVaciaVuelveAlValorPorOmisionEnVezDeRomperElArranque() {
        // `LATENCIA_PERCENTIL=` (definida pero vacia) es un caso real en un
        // compose mal rellenado. Tiene que degradar al p95 de ADR-006, no
        // impedir que el servicio arranque.
        contexto.withPropertyValues("latencia.percentil=")
                .run(ambiente -> assertThat(
                        ambiente.getBean(PropiedadesDeLatencia.class).objetivo().nombre())
                        .isEqualTo("p95"));
    }

    @Test
    void elNombreDelServicioSaleDeSpringApplicationName() {
        // Asi la muestra dice de que modulo viene sin que cada servicio tenga
        // que declarar su nombre dos veces.
        contexto.withPropertyValues("spring.application.name=salas-partidas")
                .run(ambiente -> assertThat(ambiente.getBean(RegistroDeLatencia.class).servicio())
                        .isEqualTo("salas-partidas"));
    }

    @Test
    void unServicioPuedeDesactivarlaSiLoNecesita() {
        // Valvula de escape explicita: mejor una propiedad documentada que un
        // equipo excluyendo la dependencia a mano en su build.gradle.
        contexto.withPropertyValues("latencia.activa=false")
                .run(ambiente -> assertThat(ambiente).doesNotHaveBean(FiltroDeLatencia.class));
    }

    // --- HU-REN-003: medicion de consultas a la base de datos ---

    @Test
    void tambienQuedaInstrumentadaLaBaseDeDatosSinConfigurarNada() {
        contexto.run(ambiente -> {
            assertThat(ambiente).hasSingleBean(RegistroDeConsultas.class);
            assertThat(ambiente).hasSingleBean(InstrumentadorDeDataSource.class);
        });
    }

    @Test
    void elUmbralDeConsultaLentaCaeEnElObjetivoDeRnfRen001SiNadieLoAcuerda() {
        // No se inventa un presupuesto de base de datos: los 500 ms son el
        // unico numero que existe en los requisitos. Un presupuesto propio y
        // mas estricto es una decision de equipo que aun no esta tomada.
        contexto.run(ambiente ->
                assertThat(ambiente.getBean(RegistroDeConsultas.class).umbralLentaMs()).isEqualTo(500));
    }

    @Test
    void elUmbralDeConsultaLentaSeAjustaPorVariableDeEntorno() {
        contexto.withPropertyValues("latencia.consultas.umbral-lenta-ms=120")
                .run(ambiente ->
                        assertThat(ambiente.getBean(RegistroDeConsultas.class).umbralLentaMs()).isEqualTo(120));
    }

    @Test
    void unServicioPuedeDesactivarSoloLaMedicionDeConsultas() {
        // Apagar la de base de datos no debe apagar la de peticiones: son dos
        // historias distintas y un equipo puede querer solo una.
        contexto.withPropertyValues("latencia.consultas.activa=false")
                .run(ambiente -> {
                    assertThat(ambiente).doesNotHaveBean(InstrumentadorDeDataSource.class);
                    assertThat(ambiente).doesNotHaveBean(RegistroDeConsultas.class);
                    assertThat(ambiente).hasSingleBean(FiltroDeLatencia.class);
                });
    }

    @Test
    void unServicioPuedeReemplazarElRegistroPorElSuyo() {
        contexto.withBean(RegistroDeLatencia.class, () -> new RegistroDeLatencia("el-mio", 7))
                .run(ambiente -> {
                    assertThat(ambiente).hasSingleBean(RegistroDeLatencia.class);
                    assertThat(ambiente.getBean(RegistroDeLatencia.class).servicio()).isEqualTo("el-mio");
                });
    }
}
