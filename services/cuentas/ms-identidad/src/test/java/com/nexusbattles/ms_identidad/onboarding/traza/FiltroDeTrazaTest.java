package com.nexusbattles.ms_identidad.onboarding.traza;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Traza de cada peticion entrante (regla 5, B1)")
class FiltroDeTrazaTest {

    @Test
    @DisplayName("con traceparent valido se sigue esa traza; sin el, una nueva; y al terminar no queda nada en el hilo")
    void abreYCierra() throws Exception {
        String[] vista = new String[1];
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/v1/auth/registro");
        peticion.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        new FiltroDeTraza().doFilter(peticion, new MockHttpServletResponse(),
                (p, r) -> vista[0] = Traza.actual().orElse(null));

        assertThat(vista[0]).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(Traza.actual()).isEmpty();

        new FiltroDeTraza().doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(),
                (p, r) -> vista[0] = Traza.actual().orElse(null));
        assertThat(vista[0]).matches("[0-9a-f]{32}").isNotEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(Traza.actual()).isEmpty();
    }
}
