package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificacionListaNegraService · contexto, accion y detalle")
class VerificacionListaNegraServiceTest {

    @Mock
    private CatalogoDeTerminosActivos catalogo;

    private VerificacionListaNegraService servicio;

    private static TerminoActivo termino(String texto, CategoriaDeTermino categoria) {
        String forma = NormalizadorDeTexto.compacta(texto);
        return new TerminoActivo(texto, forma, categoria, ModoDeCoincidencia.porOmision(forma));
    }

    @BeforeEach
    void preparar() {
        lenient().when(catalogo.activos()).thenReturn(List.of(
                termino("spiderman", CategoriaDeTermino.MARCA),
                termino("puta", CategoriaDeTermino.OFENSIVO),
                termino("spider", CategoriaDeTermino.OTRO)));
        servicio = new VerificacionListaNegraService(catalogo, PoliticaDeModeracion.porOmision());
    }

    @Test
    @DisplayName("un texto limpio sale aprobado con PERMITIR y sin motivo")
    void limpio() {
        var resultado = servicio.verificar("ElGuerrero", ContextoDeTexto.APODO, true);

        assertThat(resultado.aprobado()).isTrue();
        assertThat(resultado.accion()).isEqualTo(AccionDeModeracion.PERMITIR);
        assertThat(resultado.motivo()).isNull();
        assertThat(resultado.categoria()).isNull();
        assertThat(resultado.coincidencias()).isNull();
    }

    @Test
    @DisplayName("sin detalle: rechazado con motivo generico, sin termino ni categoria")
    void sinDetalle() {
        var resultado = servicio.verificar("Spider-Man", ContextoDeTexto.APODO, false);

        assertThat(resultado.aprobado()).isFalse();
        assertThat(resultado.accion()).isEqualTo(AccionDeModeracion.RECHAZAR);
        assertThat(resultado.motivo()).startsWith("El apodo no está permitido").doesNotContain("spider");
        assertThat(resultado.categoria()).isNull();
        assertThat(resultado.coincidencias()).isNull();
    }

    @Test
    @DisplayName("con detalle: la categoria del termino mas largo y todos los que coinciden, ordenados")
    void conDetalle() {
        var resultado = servicio.verificar("xXspidermanXx puta", ContextoDeTexto.APODO, true);

        assertThat(resultado.categoria()).isEqualTo(CategoriaDeTermino.MARCA);
        assertThat(resultado.coincidencias()).containsExactly("puta", "spider", "spiderman");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "APODO, RECHAZAR", "NOMBRE_EQUIPO, RECHAZAR", "NOMBRE_TORNEO, RECHAZAR", "NOMBRE_SALA, RECHAZAR",
            "COMENTARIO, REVISION", "CHAT_GENERAL, BLOQUEAR", "CHAT_SALA, BLOQUEAR", "MENSAJE_PRIVADO, BLOQUEAR",
            "GENERICO, RECHAZAR"
    })
    @DisplayName("la tabla contexto -> accion del contrato")
    void tablaDelContrato(ContextoDeTexto contexto, AccionDeModeracion accion) {
        var resultado = servicio.verificar("spiderman", contexto, false);

        assertThat(resultado.aprobado()).isFalse();
        assertThat(resultado.accion()).isEqualTo(accion);
        assertThat(resultado.motivo()).isNotBlank().doesNotContain("spiderman");
    }

    @Test
    @DisplayName("sin contexto se aplica GENERICO")
    void sinContexto() {
        assertThat(servicio.verificar("spiderman", null, false).accion()).isEqualTo(AccionDeModeracion.RECHAZAR);
        assertThat(servicio.verificar("spiderman", null, false).motivo())
                .isEqualTo(PoliticaDeModeracion.porOmision().motivo(ContextoDeTexto.GENERICO));
    }

    @Test
    @DisplayName("la politica se configura: COMENTARIO puede pasar a BLOQUEAR sin tocar codigo")
    void politicaConfigurable() {
        var bloqueaComentarios = new VerificacionListaNegraService(catalogo,
                new PoliticaDeModeracion(Map.of(ContextoDeTexto.COMENTARIO, AccionDeModeracion.BLOQUEAR)));

        assertThat(bloqueaComentarios.verificar("spiderman", ContextoDeTexto.COMENTARIO, false).accion())
                .isEqualTo(AccionDeModeracion.BLOQUEAR);
        assertThat(bloqueaComentarios.verificar("spiderman", ContextoDeTexto.APODO, false).accion())
                .as("lo no configurado conserva la tabla del contrato").isEqualTo(AccionDeModeracion.RECHAZAR);
    }

    @Test
    @DisplayName("PERMITIR no es una accion para un texto que coincide: la configuracion no arranca")
    void permitirNoSeConfigura() {
        assertThatThrownBy(() -> new PoliticaDeModeracion(
                Map.of(ContextoDeTexto.CHAT_SALA, AccionDeModeracion.PERMITIR)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHAT_SALA");
    }

    @Test
    @DisplayName("texto vacio o de mas de 2000 caracteres: 400; exactamente 2000 se acepta")
    void longitud() {
        assertThatThrownBy(() -> servicio.verificar("  ", ContextoDeTexto.APODO, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.verificar(null, ContextoDeTexto.APODO, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.verificar("a".repeat(2001), ContextoDeTexto.COMENTARIO, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");
        assertThat(servicio.verificar("a".repeat(2000), ContextoDeTexto.COMENTARIO, false).aprobado()).isTrue();
    }

    @Test
    @DisplayName("un texto invalido no llega a leer la lista")
    void invalidoNoLee() {
        var otro = new VerificacionListaNegraService(catalogo, PoliticaDeModeracion.porOmision());
        assertThatThrownBy(() -> otro.verificar("", ContextoDeTexto.APODO, true))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(catalogo);
    }

    @Test
    @DisplayName("cada contexto tiene su motivo, y ninguno nombra el termino")
    void motivos() {
        PoliticaDeModeracion politica = PoliticaDeModeracion.porOmision();
        for (ContextoDeTexto contexto : ContextoDeTexto.values()) {
            assertThat(politica.motivo(contexto)).isNotBlank();
        }
        assertThat(politica.motivo(ContextoDeTexto.COMENTARIO)).contains("revisión");
        assertThat(politica.motivo(ContextoDeTexto.CHAT_SALA)).contains("no se envió");
        assertThat(politica.motivo(null)).isEqualTo(politica.motivo(ContextoDeTexto.GENERICO));
        assertThat(politica.acciones()).hasSize(ContextoDeTexto.values().length);
    }
}
