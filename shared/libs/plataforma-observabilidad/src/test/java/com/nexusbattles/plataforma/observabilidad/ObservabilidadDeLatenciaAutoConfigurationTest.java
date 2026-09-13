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
    void sinPercentilConfiguradoLaMedicionSigueActivaYElObjetivoQuedaVacio() {
        // La medicion no depende de ninguna decision pendiente: tiene que correr
        // en los veinte modulos desde el Sprint 1. Lo que espera al Product
        // Owner (CA-03) es la EVALUACION, no la recoleccion.
        contexto.run(ambiente -> {
            assertThat(ambiente).hasSingleBean(FiltroDeLatencia.class);
            assertThat(ambiente.getBean(PropiedadesDeLatencia.class).objetivo()).isEmpty();
        });
    }

    @Test
    void elPercentilAprobadoPorElProductOwnerEntraPorVariableDeEntorno() {
        contexto.withPropertyValues("latencia.percentil=99", "latencia.objetivo-ms=500")
                .run(ambiente -> {
                    ObjetivoDeLatencia objetivo =
                            ambiente.getBean(PropiedadesDeLatencia.class).objetivo().orElseThrow();
                    assertThat(objetivo.nombre()).isEqualTo("p99");
                    assertThat(objetivo.objetivoMs()).isEqualTo(500);
                });
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

    @Test
    void unServicioPuedeReemplazarElRegistroPorElSuyo() {
        contexto.withBean(RegistroDeLatencia.class, () -> new RegistroDeLatencia("el-mio", 7))
                .run(ambiente -> {
                    assertThat(ambiente).hasSingleBean(RegistroDeLatencia.class);
                    assertThat(ambiente.getBean(RegistroDeLatencia.class).servicio()).isEqualTo("el-mio");
                });
    }
}
