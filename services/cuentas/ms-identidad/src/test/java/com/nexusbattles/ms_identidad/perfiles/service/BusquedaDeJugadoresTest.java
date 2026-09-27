package com.nexusbattles.ms_identidad.perfiles.service;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.perfiles.dto.PerfilPublicoResponse;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Las reglas de {@code GET /api/v1/perfiles/publicos} (ms-identidad-perfiles.yaml)
 * que no dependen de la base: longitud del texto, patron de prefijo con los
 * comodines escapados, solo cuentas ACTIVO y como mucho 10 resultados. Lo que
 * la consulta hace de verdad con esos datos lo prueba {@code PerfilesPublicosIT}
 * contra PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Busqueda de jugadores por prefijo del apodo (B6)")
class BusquedaDeJugadoresTest {

    @Mock
    private PerfilUsuarioRepository perfiles;

    @InjectMocks
    private BusquedaDeJugadores busqueda;

    private String patronEnviado() {
        ArgumentCaptor<String> patron = ArgumentCaptor.forClass(String.class);
        verify(perfiles).buscarPublicosPorPrefijo(patron.capture(), anyString(), any(Pageable.class));
        return patron.getValue();
    }

    @Test
    @DisplayName("pide a la base un prefijo (texto + %), solo cuentas ACTIVO y una sola pagina de 10")
    void prefijoActivasYDiez() {
        PerfilPublicoResponse ana = new PerfilPublicoResponse(UUID.randomUUID(), "Ana", "/avatares-subidos/ana.png");
        when(perfiles.buscarPublicosPorPrefijo(anyString(), anyString(), any(Pageable.class))).thenReturn(List.of(ana));

        List<PerfilPublicoResponse> encontrados = busqueda.buscar("Ana");

        assertThat(encontrados).containsExactly(ana);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(perfiles).buscarPublicosPorPrefijo(eq("Ana%"), eq(EstadoCuenta.ACTIVO), pagina.capture());
        assertThat(pagina.getValue().getPageNumber()).isZero();
        assertThat(pagina.getValue().getPageSize()).isEqualTo(BusquedaDeJugadores.MAXIMO_RESULTADOS).isEqualTo(10);
        assertThat(pagina.getValue().getSort().isUnsorted()).as("el orden lo fija la consulta, no la pagina").isTrue();
    }

    @Test
    @DisplayName("los espacios alrededor no cuentan: se busca lo que la persona escribio")
    void recortaEspacios() {
        busqueda.buscar("  lyra \t");

        assertThat(patronEnviado()).isEqualTo("lyra%");
    }

    @Test
    @DisplayName("% y _ del texto se buscan literalmente, no como comodines")
    void escapaComodines() {
        busqueda.buscar("a%b_c");

        char e = PerfilUsuarioRepository.ESCAPE_LIKE;
        assertThat(patronEnviado()).isEqualTo("a" + e + "%b" + e + "_c%");
    }

    @Test
    @DisplayName("el propio caracter de escape tambien se escapa")
    void escapaElEscape() {
        char e = PerfilUsuarioRepository.ESCAPE_LIKE;
        busqueda.buscar("uno" + e + "dos");

        assertThat(patronEnviado()).isEqualTo("uno" + e + e + "dos%");
    }

    @Test
    @DisplayName("tres caracteres bastan; cincuenta tambien (lo que mide un apodo)")
    void limitesAdmitidos() {
        busqueda.buscar("abc");
        busqueda.buscar("x".repeat(50));

        ArgumentCaptor<String> patron = ArgumentCaptor.forClass(String.class);
        verify(perfiles, org.mockito.Mockito.times(2))
                .buscarPublicosPorPrefijo(patron.capture(), anyString(), any(Pageable.class));
        assertThat(patron.getAllValues()).containsExactly("abc%", "x".repeat(50) + "%");
    }

    @Test
    @DisplayName("la longitud se cuenta en caracteres, no en unidades UTF-16: tres emojis son tres")
    void cuentaCaracteresReales() {
        busqueda.buscar("🐉🐉🐉");

        assertThat(patronEnviado()).isEqualTo("🐉🐉🐉%");
    }

    @ParameterizedTest(name = "«{0}» no llega a tres caracteres")
    @NullSource
    @ValueSource(strings = {"", "   ", "ab", "  ab  ", "🐉🐉"})
    @DisplayName("menos de tres caracteres (o nada): se rechaza sin tocar la base")
    void demasiadoCorto(String texto) {
        assertThatThrownBy(() -> busqueda.buscar(texto))
                .isInstanceOf(BusquedaInvalidaException.class)
                .hasMessageContaining("3");
        verifyNoInteractions(perfiles);
    }

    @Test
    @DisplayName("mas de cincuenta caracteres: ningun apodo puede empezar asi, se rechaza sin tocar la base")
    void demasiadoLargo() {
        assertThatThrownBy(() -> busqueda.buscar("x".repeat(51)))
                .isInstanceOf(BusquedaInvalidaException.class)
                .hasMessageContaining("50");
        verifyNoInteractions(perfiles);
    }
}
