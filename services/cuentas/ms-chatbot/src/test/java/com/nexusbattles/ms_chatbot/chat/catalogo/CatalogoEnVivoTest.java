package com.nexusbattles.ms_chatbot.chat.catalogo;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 1.10.0 (7.4.5): el catalogo se lee entero, se guarda una copia y se renueva sola.
class CatalogoEnVivoTest {

    private static ProductoDelCatalogoDto producto(String nombre) {
        return new ProductoDelCatalogoDto("id-" + nombre, nombre, "", "ARMA", 10, null, false, null);
    }

    private static final class RelojMovible extends Clock {
        private Instant ahora = Instant.parse("2026-10-14T12:00:00Z");

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    @Test
    void leeTodasLasPaginasYDescartaLosProductosSinNombre() {
        CatalogoClient cliente = mock(CatalogoClient.class);
        when(cliente.pagina(0, 50)).thenReturn(new PaginaDelCatalogoDto(
            List.of(producto("Espada"), new ProductoDelCatalogoDto("x", " ", null, "ARMA", 1, null, false, null)), 0, 2,
            3));
        when(cliente.pagina(1, 50)).thenReturn(new PaginaDelCatalogoDto(List.of(producto("Escudo")), 1, 2, 3));

        CatalogoEnVivo catalogo = new CatalogoEnVivo(cliente, new RelojMovible());

        assertThat(catalogo.productos()).hasValueSatisfying(lista ->
            assertThat(lista).extracting(ProductoDelCatalogoDto::nombre).containsExactly("Espada", "Escudo"));
    }

    @Test
    void guardaLaCopiaDuranteSuVigenciaYLaRenuevaDespues() {
        CatalogoClient cliente = mock(CatalogoClient.class);
        when(cliente.pagina(0, 50))
            .thenReturn(new PaginaDelCatalogoDto(List.of(producto("Espada")), 0, 1, 1))
            .thenReturn(new PaginaDelCatalogoDto(List.of(producto("Espada"), producto("Hacha")), 0, 1, 2));
        RelojMovible reloj = new RelojMovible();
        CatalogoEnVivo catalogo = new CatalogoEnVivo(cliente, reloj);

        assertThat(catalogo.productos().orElseThrow()).hasSize(1);
        reloj.avanzar(CatalogoEnVivo.VIGENCIA.minusSeconds(1));
        assertThat(catalogo.productos().orElseThrow()).hasSize(1);
        reloj.avanzar(Duration.ofSeconds(2));
        assertThat(catalogo.productos().orElseThrow()).hasSize(2);
        verify(cliente, times(2)).pagina(0, 50);
    }

    @Test
    void siElCatalogoNoRespondeUsaLaCopiaRecienteYLuegoNada() {
        CatalogoClient cliente = mock(CatalogoClient.class);
        when(cliente.pagina(0, 50))
            .thenReturn(new PaginaDelCatalogoDto(List.of(producto("Espada")), 0, 1, 1))
            .thenThrow(new ResourceAccessException("caido"));
        RelojMovible reloj = new RelojMovible();
        CatalogoEnVivo catalogo = new CatalogoEnVivo(cliente, reloj);
        assertThat(catalogo.productos()).isPresent();

        reloj.avanzar(CatalogoEnVivo.VIGENCIA.plusMinutes(1));
        assertThat(catalogo.productos()).as("copia de hace 6 min").isPresent();

        reloj.avanzar(CatalogoEnVivo.VIGENCIA_SI_FALLA);
        assertThat(catalogo.productos()).as("copia de hace mas de 30 min").isEmpty();
    }

    @Test
    void sinCopiaYConElCatalogoCaidoNoHayNada() {
        CatalogoClient cliente = mock(CatalogoClient.class);
        when(cliente.pagina(0, 50)).thenThrow(new ResourceAccessException("caido"));

        assertThat(new CatalogoEnVivo(cliente, new RelojMovible()).productos()).isEmpty();
    }

    @Test
    void unaPaginaVaciaONulaTerminaLaLectura() {
        CatalogoClient cliente = mock(CatalogoClient.class);
        when(cliente.pagina(0, 50)).thenReturn(null);

        assertThat(new CatalogoEnVivo(cliente, new RelojMovible()).productos()).hasValue(List.of());
    }
}
