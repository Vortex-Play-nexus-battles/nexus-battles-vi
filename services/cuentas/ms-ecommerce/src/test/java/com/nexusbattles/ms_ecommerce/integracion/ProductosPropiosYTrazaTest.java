package com.nexusbattles.ms_ecommerce.integracion;

import com.nexusbattles.ms_ecommerce.integracion.inventario.ClienteDeInventario;
import com.nexusbattles.ms_ecommerce.integracion.inventario.ProductosPropios;
import com.nexusbattles.ms_ecommerce.seguridad.CredencialDeServicio;
import com.nexusbattles.ms_ecommerce.traza.FiltroDeTraza;
import com.nexusbattles.ms_ecommerce.traza.Traza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Lo propio del jugador (nunca tumba la vitrina) y la traza de cada peticion")
class ProductosPropiosYTrazaTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);

    private static CredencialDeServicio credencial(boolean configurada) {
        return new CredencialDeServicio() {
            @Override
            public String portador() {
                return "token";
            }

            @Override
            public boolean configurada() {
                return configurada;
            }
        };
    }

    @AfterEach
    void limpiar() {
        Traza.cerrar();
    }

    @Test
    @DisplayName("lee el inventario una vez y lo recuerda 30 s por jugador")
    void copiaPorJugador() {
        ClienteDeInventario inventario = mock(ClienteDeInventario.class);
        when(inventario.productosDe("uid")).thenReturn(Set.of("a", "b"));
        ProductosPropios propios = new ProductosPropios(inventario, credencial(true), RELOJ);

        assertThat(propios.de("uid")).containsExactlyInAnyOrder("a", "b");
        assertThat(propios.de("uid")).containsExactlyInAnyOrder("a", "b");

        verify(inventario, times(1)).productosDe("uid");
    }

    @Test
    @DisplayName("RF-CAR-004: para decidir se pregunta al inventario ahora, sin la copia, y la respuesta la renueva")
    void alDiaSinCopia() {
        ClienteDeInventario inventario = mock(ClienteDeInventario.class);
        when(inventario.productosDe("uid")).thenReturn(Set.of("a")).thenReturn(Set.of("a", "b"));
        ProductosPropios propios = new ProductosPropios(inventario, credencial(true), RELOJ);

        assertThat(propios.de("uid")).containsExactly("a");
        // Lo acaba de comprar: la copia de la vitrina no lo sabe, la consulta al dia si.
        assertThat(propios.alDia("uid")).containsExactlyInAnyOrder("a", "b");
        assertThat(propios.de("uid")).containsExactlyInAnyOrder("a", "b");

        verify(inventario, times(2)).productosDe("uid");
    }

    @Test
    @DisplayName("una compra entregada olvida la copia: la vitrina siguiente pregunta y ya lo marca como propio")
    void olvidarTrasLaCompra() {
        ClienteDeInventario inventario = mock(ClienteDeInventario.class);
        when(inventario.productosDe("uid")).thenReturn(Set.of()).thenReturn(Set.of("espada"));
        ProductosPropios propios = new ProductosPropios(inventario, credencial(true), RELOJ);

        // Al añadirlo a la cesta (RF-CAR-004) todavia no era suyo: esa respuesta queda como copia.
        assertThat(propios.alDia("uid")).isEmpty();
        propios.olvidar("uid");

        assertThat(propios.de("uid")).containsExactly("espada");
        verify(inventario, times(2)).productosDe("uid");
        // Olvidar a quien no tiene copia, o a nadie, no falla.
        propios.olvidar("otro");
        propios.olvidar(null);
    }

    @Test
    @DisplayName("al dia y con el inventario caido, o sin credencial: nada, sin excepcion")
    void alDiaSinInventario() {
        ClienteDeInventario caido = mock(ClienteDeInventario.class);
        when(caido.productosDe("uid")).thenThrow(new ServicioNoDisponibleException("inventario", "caido"));
        ClienteDeInventario sinUsar = mock(ClienteDeInventario.class);

        assertThat(new ProductosPropios(caido, credencial(true), RELOJ).alDia("uid")).isEmpty();
        assertThat(new ProductosPropios(sinUsar, credencial(false), RELOJ).alDia("uid")).isEmpty();
        assertThat(new ProductosPropios(sinUsar, credencial(true), RELOJ).alDia(null)).isEmpty();
        verifyNoInteractions(sinUsar);
    }

    @Test
    @DisplayName("inventario caido: nada marcado, sin excepcion")
    void inventarioCaido() {
        ClienteDeInventario inventario = mock(ClienteDeInventario.class);
        when(inventario.productosDe("uid")).thenThrow(new ServicioNoDisponibleException("inventario", "caido"));

        assertThat(new ProductosPropios(inventario, credencial(true), RELOJ).de("uid")).isEmpty();
    }

    @Test
    @DisplayName("sin credencial, o sin jugador, ni se pregunta")
    void sinCredencialNiJugador() {
        ClienteDeInventario inventario = mock(ClienteDeInventario.class);

        assertThat(new ProductosPropios(inventario, credencial(false), RELOJ).de("uid")).isEmpty();
        assertThat(new ProductosPropios(inventario, credencial(true), RELOJ).de(" ")).isEmpty();
        verifyNoInteractions(inventario);
    }

    @Test
    @DisplayName("el filtro abre la traza del traceparent que llega y la cierra al terminar")
    void filtroConTraceparent() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        AtomicReference<String> vista = new AtomicReference<>();
        MockFilterChain cadena = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                vista.set(Traza.actual().orElse(null) + "|" + MDC.get(Traza.CLAVE_MDC));
            }
        };

        new FiltroDeTraza().doFilter(peticion, new MockHttpServletResponse(), cadena);

        assertThat(vista.get()).isEqualTo("0af7651916cd43dd8448eb211c80319c|0af7651916cd43dd8448eb211c80319c");
        assertThat(Traza.actual()).isEmpty();
    }

    @Test
    @DisplayName("sin traceparent valido, una traza nueva; el hijo lleva el mismo trace con otro span")
    void trazaNueva() {
        assertThat(Traza.traceIdDe("basura")).isEmpty();
        assertThat(Traza.traceIdDe(null)).isEmpty();
        assertThat(Traza.traceIdDe("00-00000000000000000000000000000000-b7ad6b7169203331-01")).isEmpty();

        String traceId = Traza.abrir(null);

        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(Traza.traceparentHijo()).hasValueSatisfying(
                hijo -> assertThat(hijo).startsWith("00-" + traceId + "-").endsWith("-01"));
    }
}
