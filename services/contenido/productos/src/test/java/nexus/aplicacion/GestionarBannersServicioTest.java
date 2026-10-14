package nexus.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import nexus.api.SolicitudBanner;
import nexus.auditoria.RegistroDeOperaciones;
import nexus.auditoria.TipoOperacion;
import nexus.dominio.Banner;
import nexus.dominio.BannerNoEncontradoException;
import nexus.persistencia.BannerRepository;
import nexus.recursos.MotivoInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Banners: autor, historial y motivo del retiro (SA-18)")
class GestionarBannersServicioTest {

        private static final Instant CREADO = Instant.parse("2026-10-01T10:00:00Z");

        private final BannerRepository repositorio = mock(BannerRepository.class);
        private final RegistroDeOperaciones operaciones = mock(RegistroDeOperaciones.class);
        private final GestionarBannersServicio servicio = new GestionarBannersServicio(repositorio, operaciones);

        private final Banner existente = new Banner("banner-1", "Mensaje anterior", Instant.parse("2026-10-02T00:00:00Z"),
                        Instant.parse("2099-01-01T00:00:00Z"), false, CREADO, CREADO, "uid-ana", "uid-ana");

        private static SolicitudBanner solicitud(String contenido) {
                return new SolicitudBanner(contenido, Instant.parse("2026-10-20T00:00:00Z"),
                                Instant.parse("2099-02-01T00:00:00Z"));
        }

        @Test
        @DisplayName("editar conserva quién lo creó, anota quién lo editó y registra el antes y el después")
        void editar() {
                when(repositorio.findById("banner-1")).thenReturn(Optional.of(existente));
                when(repositorio.save(any(Banner.class))).thenAnswer(i -> i.getArgument(0, Banner.class));

                Banner editado = servicio.editar("banner-1", solicitud("  Mensaje nuevo  "), "uid-luis");

                assertThat(editado.contenido()).isEqualTo("Mensaje nuevo");
                assertThat(editado.creadoPor()).isEqualTo("uid-ana");
                assertThat(editado.modificadoPor()).isEqualTo("uid-luis");
                assertThat(editado.creadoEn()).isEqualTo(CREADO);
                verify(operaciones).deBanner(TipoOperacion.MODIFICACION_BANNER, existente, editado, "uid-luis", null);
        }

        @Test
        @DisplayName("un banner anterior a SA-18, sin autores, se puede retirar y queda quién lo hizo")
        void retirarUnoAnterior() {
                Banner sinAutores = new Banner("banner-2", "Viejo", CREADO, Instant.parse("2099-01-01T00:00:00Z"),
                                false, CREADO, CREADO);
                when(repositorio.findById("banner-2")).thenReturn(Optional.of(sinAutores));
                when(repositorio.save(any(Banner.class))).thenAnswer(i -> i.getArgument(0, Banner.class));

                servicio.retirar("banner-2", "uid-luis", "  Ya pasó el evento ");

                ArgumentCaptor<Banner> guardado = ArgumentCaptor.forClass(Banner.class);
                verify(repositorio).save(guardado.capture());
                assertThat(guardado.getValue().retirado()).isTrue();
                assertThat(guardado.getValue().creadoPor()).isNull();
                assertThat(guardado.getValue().modificadoPor()).isEqualTo("uid-luis");
                verify(operaciones).deBanner(eq(TipoOperacion.RETIRO_BANNER), eq(sinAutores), any(Banner.class),
                                eq("uid-luis"), eq("Ya pasó el evento"));
        }

        @Test
        @DisplayName("un motivo de más de 500 caracteres se rechaza antes de buscar el banner")
        void motivoLargo() {
                assertThatThrownBy(() -> servicio.retirar("banner-1", "uid-luis", "x".repeat(501)))
                                .isInstanceOf(MotivoInvalidoException.class);
                verifyNoInteractions(repositorio, operaciones);
        }

        @Test
        @DisplayName("el historial de un banner inexistente es 404, no una lista vacía")
        void historialDeInexistente() {
                when(repositorio.findById("nada")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> servicio.historial("nada")).isInstanceOf(BannerNoEncontradoException.class);
                verifyNoInteractions(operaciones);
        }
}
