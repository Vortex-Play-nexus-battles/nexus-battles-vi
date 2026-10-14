package com.nexusbattles.plataforma.adminparametros.politicas;

import com.nexusbattles.plataforma.adminparametros.parametros.Actor;
import com.nexusbattles.plataforma.adminparametros.parametros.ParametroRechazado;
import com.nexusbattles.plataforma.adminparametros.parametros.ParametroRechazado.Motivo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SA-11 · publicar un cambio en las politicas de uso (§7.3.7)")
class PoliticasServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final OffsetDateTime EN_UTC = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final UUID ADMIN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final CambioDePoliticaRepository cambios = mock(CambioDePoliticaRepository.class);
    private final PoliticasService servicio = new PoliticasService(cambios, Clock.fixed(AHORA, ZoneOffset.UTC));

    private static PoliticasService.Publicacion pedido(String politica, String titulo, String descripcion,
                                                       OffsetDateTime vigencia, String motivo) {
        return new PoliticasService.Publicacion(politica, titulo, descripcion, vigencia, motivo);
    }

    private static PoliticasService.Publicacion valido() {
        return pedido("TERMINOS_Y_CONDICIONES", "  Nuevas reglas del chat  ",
                "  Los mensajes con enlaces externos se ocultan hasta que un moderador los revise.  ",
                EN_UTC.plusDays(6), "  Acuerdo del comité  ");
    }

    private void rechazado(Actor actor, PoliticasService.Publicacion pedido, Motivo motivo) {
        assertThatThrownBy(() -> servicio.publicar(actor, pedido, "203.0.113.9"))
                .isInstanceOfSatisfying(ParametroRechazado.class, ex -> assertThat(ex.motivo()).isEqualTo(motivo));
    }

    @Test
    @DisplayName("Administrador y Super Administrador publican; queda con su autor, su IP, sin espacios de mas")
    void publica() {
        when(cambios.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        for (String rol : List.of("ADMINISTRADOR", "SUPER_ADMINISTRADOR")) {
            CambioDePolitica hecho = servicio.publicar(new Actor(ADMIN, rol), valido(), "203.0.113.9");

            assertThat(hecho.politica()).isEqualTo(Politica.TERMINOS_Y_CONDICIONES);
            assertThat(hecho.titulo()).isEqualTo("Nuevas reglas del chat");
            assertThat(hecho.descripcion()).startsWith("Los mensajes").endsWith("revise.");
            assertThat(hecho.motivo()).isEqualTo("Acuerdo del comité");
            assertThat(hecho.vigenteDesde()).isEqualTo(EN_UTC.plusDays(6));
            assertThat(hecho.publicadoPor()).isEqualTo(ADMIN);
            assertThat(hecho.publicadoEn()).isEqualTo(EN_UTC);
            assertThat(hecho.ipOrigen()).isEqualTo("203.0.113.9");
            assertThat(hecho.auditadoEn()).as("lo audita el envio, no la peticion").isNull();
        }
    }

    @Test
    @DisplayName("sin vigencia rige desde ya; una vigencia de hace un par de minutos se admite (relojes)")
    void vigencia() {
        when(cambios.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        Actor admin = new Actor(ADMIN, "ADMINISTRADOR");

        CambioDePolitica ya = servicio.publicar(admin, pedido("POLITICA_DE_PRIVACIDAD", "Datos del chat",
                "Los mensajes del chat se guardan noventa días y luego se borran.", null, "Ley de datos"), null);
        assertThat(ya.vigenteDesde()).isEqualTo(EN_UTC);
        assertThat(ya.politica().nombre()).isEqualTo("Política de privacidad");

        CambioDePolitica holgura = servicio.publicar(admin, pedido("POLITICA_DE_PRIVACIDAD", "Datos del chat",
                "Los mensajes del chat se guardan noventa días y luego se borran.", EN_UTC.minusMinutes(2),
                "Ley de datos"), null);
        assertThat(holgura.vigenteDesde()).isEqualTo(EN_UTC.minusMinutes(2));
    }

    @Test
    @DisplayName("jugador, moderador, servicio o sin sesion: 403 y no se guarda nada")
    void permisos() {
        rechazado(new Actor(UUID.randomUUID(), "JUGADOR"), valido(), Motivo.PERMISO_INSUFICIENTE);
        rechazado(new Actor(UUID.randomUUID(), "MODERADOR"), valido(), Motivo.PERMISO_INSUFICIENTE);
        rechazado(new Actor(null, "SERVICIO"), valido(), Motivo.PERMISO_INSUFICIENTE);
        rechazado(new Actor(null, "ADMINISTRADOR"), valido(), Motivo.PERMISO_INSUFICIENTE);
        rechazado(null, valido(), Motivo.PERMISO_INSUFICIENTE);
        verify(cambios, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "NORMAS", "terminos_y_condiciones"})
    @DisplayName("una politica que no existe es una solicitud invalida")
    void politicaDesconocida(String politica) {
        rechazado(new Actor(ADMIN, "ADMINISTRADOR"), pedido(politica, "Nuevas reglas",
                "Los mensajes con enlaces externos se ocultan.", null, "Comité"), Motivo.SOLICITUD_INVALIDA);
        rechazado(new Actor(ADMIN, "ADMINISTRADOR"), pedido(null, "Nuevas reglas",
                "Los mensajes con enlaces externos se ocultan.", null, "Comité"), Motivo.SOLICITUD_INVALIDA);
        verify(cambios, never()).save(any());
    }

    @Test
    @DisplayName("titulo, lo que cambia y motivo con su largo; la vigencia no puede estar en el pasado")
    void validaciones() {
        Actor admin = new Actor(ADMIN, "SUPER_ADMINISTRADOR");
        String descripcion = "Los mensajes con enlaces externos se ocultan.";
        rechazado(admin, pedido("TERMINOS_Y_CONDICIONES", "Algo", descripcion, null, "Comité"),
                Motivo.SOLICITUD_INVALIDA);
        rechazado(admin, pedido("TERMINOS_Y_CONDICIONES", "x".repeat(121), descripcion, null, "Comité"),
                Motivo.SOLICITUD_INVALIDA);
        rechazado(admin, pedido("TERMINOS_Y_CONDICIONES", "Nuevas reglas", "Muy corto", null, "Comité"),
                Motivo.SOLICITUD_INVALIDA);
        rechazado(admin, pedido("TERMINOS_Y_CONDICIONES", "Nuevas reglas", "x".repeat(2001), null, "Comité"),
                Motivo.SOLICITUD_INVALIDA);
        rechazado(admin, pedido("TERMINOS_Y_CONDICIONES", "Nuevas reglas", descripcion, null, " a "),
                Motivo.SOLICITUD_INVALIDA);
        rechazado(admin, pedido("TERMINOS_Y_CONDICIONES", "Nuevas reglas", descripcion, null, null),
                Motivo.SOLICITUD_INVALIDA);
        rechazado(admin, pedido("TERMINOS_Y_CONDICIONES", "Nuevas reglas", descripcion, null, "x".repeat(501)),
                Motivo.SOLICITUD_INVALIDA);
        rechazado(admin, pedido("TERMINOS_Y_CONDICIONES", "Nuevas reglas", descripcion,
                EN_UTC.minusMinutes(6), "Comité"), Motivo.SOLICITUD_INVALIDA);
        verify(cambios, never()).save(any());
    }

    @Test
    @DisplayName("el historial pide los 50 mas recientes; lo posterior exige desde y acota el lote a 1..100")
    void lecturas() {
        when(cambios.findAllByOrderByPublicadoEnDescIdDesc(any(Pageable.class))).thenReturn(List.of());
        assertThat(servicio.historial()).isEmpty();
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(cambios).findAllByOrderByPublicadoEnDescIdDesc(pagina.capture());
        assertThat(pagina.getValue().getPageSize()).isEqualTo(50);

        assertThatThrownBy(() -> servicio.posteriores(null, 10))
                .isInstanceOfSatisfying(ParametroRechazado.class,
                        ex -> assertThat(ex.motivo()).isEqualTo(Motivo.SOLICITUD_INVALIDA));

        when(cambios.posterioresA(eq(EN_UTC), any(Pageable.class))).thenReturn(List.of());
        servicio.posteriores(EN_UTC, 500);
        servicio.posteriores(EN_UTC, 0);
        ArgumentCaptor<Pageable> lotes = ArgumentCaptor.forClass(Pageable.class);
        verify(cambios, org.mockito.Mockito.times(2)).posterioresA(eq(EN_UTC), lotes.capture());
        assertThat(lotes.getAllValues()).extracting(Pageable::getPageSize).containsExactly(100, 1);
    }
}
