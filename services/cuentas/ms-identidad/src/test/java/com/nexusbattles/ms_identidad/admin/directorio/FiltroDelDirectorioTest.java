package com.nexusbattles.ms_identidad.admin.directorio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los filtros del directorio tal como llegan de la consulta — HU-USR-008 (#561).
 *
 * <p>Lo que se prueba es que el servidor manda sobre la consulta: lo que no se
 * puede aplicar se rechaza con un motivo legible (400 {@code datos-invalidos}),
 * en vez de convertirse en una lista vacia que pareceria un resultado; y lo que
 * viene en blanco es «sin filtro», no un filtro por la cadena vacia.
 */
@DisplayName("Filtros del directorio (HU-USR-008)")
class FiltroDelDirectorioTest {

    @Test
    @DisplayName("sin nada, no hay filtros: el directorio usa la consulta de siempre")
    void sinNadaNoHayFiltros() {
        FiltroDelDirectorio filtro = FiltroDelDirectorio.de(null, false, null, null, null, null);

        assertThat(filtro.sinFiltros()).isTrue();
        assertThat(filtro.texto()).isEmpty();
        assertThat(filtro.rol()).isNull();
        assertThat(filtro.estado()).isNull();
        assertThat(filtro.registradoDesde()).isNull();
        assertThat(filtro.registradoHasta()).isNull();
    }

    @Test
    @DisplayName("lo que llega en blanco es «sin filtro», no un filtro por la cadena vacia")
    void blancoEsSinFiltro() {
        FiltroDelDirectorio filtro = FiltroDelDirectorio.de("   ", false, " ", "", "  ", "");

        assertThat(filtro.sinFiltros()).isTrue();
    }

    @Test
    @DisplayName("el texto se recorta, como el directorio de siempre")
    void recortaElTexto() {
        FiltroDelDirectorio filtro = FiltroDelDirectorio.de("  Ana Perez  ", false, null, null, null, null);

        assertThat(filtro.texto()).isEqualTo("Ana Perez");
        assertThat(filtro.sinFiltros()).isFalse();
    }

    @Test
    @DisplayName("ocultar las cuentas de pruebas ya es un filtro")
    void ocultarPruebasEsUnFiltro() {
        assertThat(FiltroDelDirectorio.de(null, true, null, null, null, null).sinFiltros()).isFalse();
    }

    @Test
    @DisplayName("rol y estado se aceptan sin distinguir mayusculas y se guardan como el contrato")
    void rolYEstadoComoElContrato() {
        FiltroDelDirectorio filtro = FiltroDelDirectorio.de(null, false, " moderador ", "suspendido", null, null);

        assertThat(filtro.rol()).isEqualTo("MODERADOR");
        assertThat(filtro.estado()).isEqualTo("SUSPENDIDO");
        assertThat(filtro.sinFiltros()).isFalse();
    }

    @Test
    @DisplayName("un rol que no existe es 400 con motivo, no una lista vacia")
    void rolDesconocido() {
        assertThatThrownBy(() -> FiltroDelDirectorio.de(null, false, "PIRATA", null, null, null))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageContaining("rol")
                .hasMessageContaining("SUPER_ADMINISTRADOR");
    }

    @Test
    @DisplayName("un estado que no existe es 400 con motivo; las formas anteriores a B2 no se piden")
    void estadoDesconocido() {
        assertThatThrownBy(() -> FiltroDelDirectorio.de(null, false, null, "DORMIDO", null, null))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageContaining("estado")
                .hasMessageContaining("BANEADO");
        assertThatThrownBy(() -> FiltroDelDirectorio.de(null, false, null, "SUSPENDIDA", null, null))
                .isInstanceOf(ConsultaInvalidaException.class);
    }

    @Test
    @DisplayName("las fechas de registro son dias, las dos inclusive")
    void fechasDeRegistro() {
        FiltroDelDirectorio filtro = FiltroDelDirectorio.de(null, false, null, null, "2026-09-01", "2026-09-30");

        assertThat(filtro.registradoDesde()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(filtro.registradoHasta()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(filtro.sinFiltros()).isFalse();
    }

    @Test
    @DisplayName("un mismo dia como desde y hasta es valido: las cuentas de ese dia")
    void unSoloDia() {
        FiltroDelDirectorio filtro = FiltroDelDirectorio.de(null, false, null, null, "2026-09-01", "2026-09-01");

        assertThat(filtro.registradoDesde()).isEqualTo(filtro.registradoHasta());
    }

    @Test
    @DisplayName("una fecha mal escrita es 400 y dice cual y como se escribe")
    void fechaMalEscrita() {
        assertThatThrownBy(() -> FiltroDelDirectorio.de(null, false, null, null, "01/09/2026", null))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageContaining("registradoDesde")
                .hasMessageContaining("aaaa-mm-dd");
        assertThatThrownBy(() -> FiltroDelDirectorio.de(null, false, null, null, null, "2026-02-30"))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageContaining("registradoHasta");
    }

    @Test
    @DisplayName("desde posterior a hasta es 400: no hay ninguna cuenta que pueda cumplirlo")
    void desdePosteriorAHasta() {
        assertThatThrownBy(() -> FiltroDelDirectorio.de(null, false, null, null, "2026-10-02", "2026-10-01"))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    @DisplayName("dia(): la fecha del contrato o un 400 que nombra el campo")
    void diaNombraElCampo() {
        assertThat(FiltroDelDirectorio.dia("2026-10-05", "desde")).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(FiltroDelDirectorio.dia("  ", "desde")).isNull();
        assertThat(FiltroDelDirectorio.dia(null, "desde")).isNull();
        assertThatThrownBy(() -> FiltroDelDirectorio.dia("ayer", "hasta"))
                .isInstanceOf(ConsultaInvalidaException.class)
                .hasMessageContaining("«hasta»");
    }
}
