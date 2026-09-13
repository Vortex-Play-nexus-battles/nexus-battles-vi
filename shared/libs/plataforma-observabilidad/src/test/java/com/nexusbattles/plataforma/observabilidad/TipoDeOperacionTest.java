package com.nexusbattles.plataforma.observabilidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * HU-REN-002 — el etiquetado que exige la restriccion de la historia:
 * «diferenciar entre consultas de lectura y operaciones de escritura».
 */
class TipoDeOperacionTest {

    private static final Instant AHORA = Instant.parse("2026-09-12T10:00:00Z");
    private static final ObjetivoDeLatencia P95 = new ObjetivoDeLatencia(500, 95);

    private static final String LISTADO = "/api/v1/subastas";
    private static final String PUJA = "/api/v1/subastas/{subastaId}/pujas";

    private final RegistroDeLatencia registro = new RegistroDeLatencia("ms-subastas");

    private void medir(String metodo, String ruta, long... duraciones) {
        for (long duracion : duraciones) {
            registro.registrar(new MuestraDeLatencia("ms-subastas", metodo, ruta, 200, duracion, AHORA));
        }
    }

    @Test
    void consultarEsLecturaYPujarEsEscritura() {
        assertThat(TipoDeOperacion.deMetodo("GET")).isEqualTo(TipoDeOperacion.LECTURA);
        assertThat(TipoDeOperacion.deMetodo("HEAD")).isEqualTo(TipoDeOperacion.LECTURA);
        assertThat(TipoDeOperacion.deMetodo("POST")).isEqualTo(TipoDeOperacion.ESCRITURA);
        assertThat(TipoDeOperacion.deMetodo("PUT")).isEqualTo(TipoDeOperacion.ESCRITURA);
        assertThat(TipoDeOperacion.deMetodo("PATCH")).isEqualTo(TipoDeOperacion.ESCRITURA);
        assertThat(TipoDeOperacion.deMetodo("DELETE")).isEqualTo(TipoDeOperacion.ESCRITURA);
    }

    @Test
    void elPreflightDeCorsNoEnsuciaElPercentilDeLecturas() {
        // OPTIONS no es trafico de jugador. Contarlo como lectura bajaria el
        // percentil de los listados con peticiones que el navegador hace solo.
        assertThat(TipoDeOperacion.deMetodo("OPTIONS")).isEqualTo(TipoDeOperacion.OTRA);
        assertThat(TipoDeOperacion.deMetodo("TRACE")).isEqualTo(TipoDeOperacion.OTRA);
    }

    @Test
    void elMetodoSeReconoceVengaComoVenga() {
        assertThat(TipoDeOperacion.deMetodo("get")).isEqualTo(TipoDeOperacion.LECTURA);
        assertThat(TipoDeOperacion.deMetodo("  post  ")).isEqualTo(TipoDeOperacion.ESCRITURA);
    }

    @Test
    void unMetodoAusenteNoRompeLaMedicion() {
        assertThat(TipoDeOperacion.deMetodo(null)).isEqualTo(TipoDeOperacion.OTRA);
        assertThat(TipoDeOperacion.deMetodo("")).isEqualTo(TipoDeOperacion.OTRA);
    }

    @Test
    void cadaMuestraSabeSiFueLecturaOEscritura() {
        MuestraDeLatencia listado = new MuestraDeLatencia("ms-subastas", "GET", LISTADO, 200, 40, AHORA);
        MuestraDeLatencia puja = new MuestraDeLatencia("ms-subastas", "POST", PUJA, 201, 90, AHORA);

        assertThat(listado.tipo()).isEqualTo(TipoDeOperacion.LECTURA);
        assertThat(puja.tipo()).isEqualTo(TipoDeOperacion.ESCRITURA);
    }

    @Test
    void elResumenSeparaLasDosFamiliasEnVezDeMezclarlas() {
        // Este es el punto de la historia. Con un solo percentil, las lecturas
        // —que son muchisimas mas— entierran a las escrituras y el numero deja
        // de decir nada sobre la puja, que es la que de verdad duele.
        for (int i = 0; i < 99; i++) {
            medir("GET", LISTADO, 10);
        }
        medir("POST", PUJA, 900);

        List<InformeDeLatencia.ResumenPorTipo> resumen = registro.resumenPorTipo(P95);

        assertThat(registro.informe(P95, 5).percentilMs())
                .as("el percentil global esconde la puja lenta")
                .isEqualTo(10);

        assertThat(resumen).hasSize(2);
        assertThat(resumen.get(0).tipo()).isEqualTo(TipoDeOperacion.LECTURA);
        assertThat(resumen.get(0).muestras()).isEqualTo(99);
        assertThat(resumen.get(0).percentilMs()).isEqualTo(10);
        assertThat(resumen.get(1).tipo()).isEqualTo(TipoDeOperacion.ESCRITURA);
        assertThat(resumen.get(1).muestras()).isEqualTo(1);
        assertThat(resumen.get(1).percentilMs()).isEqualTo(900);
    }

    @Test
    void elOrdenDelResumenNoCambiaEntreDosConsultas() {
        medir("POST", PUJA, 90);
        medir("GET", LISTADO, 40);

        assertThat(registro.resumenPorTipo(P95))
                .extracting(InformeDeLatencia.ResumenPorTipo::etiqueta)
                .containsExactly("lectura", "escritura");
    }

    @Test
    void unTipoSinMuestrasNoAparece() {
        // Una fila «escritura: 0 muestras, 0 ms» invita a leer el cero como
        // «va rapidisimo» cuando lo que pasa es que nadie ha escrito nada.
        medir("GET", LISTADO, 40, 50);

        assertThat(registro.resumenPorTipo(P95))
                .extracting(InformeDeLatencia.ResumenPorTipo::tipo)
                .containsExactly(TipoDeOperacion.LECTURA);
    }

    @Test
    void sinMuestrasElResumenEstaVacio() {
        assertThat(registro.resumenPorTipo(P95)).isEmpty();
    }

    @Test
    void elMaximoPorTipoSeReportaJuntoAlPercentil() {
        medir("POST", PUJA, 100, 200, 4000);

        InformeDeLatencia.ResumenPorTipo escrituras = registro.resumenPorTipo(P95).get(0);

        assertThat(escrituras.percentilMs()).isEqualTo(4000);
        assertThat(escrituras.maximoMs()).isEqualTo(4000);
    }
}
