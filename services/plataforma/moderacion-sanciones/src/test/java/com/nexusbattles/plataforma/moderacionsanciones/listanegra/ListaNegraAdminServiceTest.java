package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListaNegraAdminService · la forma normalizada es la identidad del termino")
class ListaNegraAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T10:00:00Z");

    @Mock
    private TerminoProhibidoRepository repositorio;

    @Mock
    private CatalogoDeTerminosActivos catalogo;

    private ListaNegraAdminService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ListaNegraAdminService(repositorio, catalogo, Clock.fixed(AHORA, ZoneOffset.UTC));
        lenient().when(repositorio.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static TerminoProhibido existente(String termino, CategoriaDeTermino categoria, ModoDeCoincidencia modo) {
        return new TerminoProhibido(termino, NormalizadorDeTexto.compacta(termino), categoria, modo, true, "semilla",
                OffsetDateTime.ofInstant(AHORA.minusSeconds(3600), ZoneOffset.UTC));
    }

    private static ListaNegraAdminService.DatosDeTermino datos(String termino) {
        return new ListaNegraAdminService.DatosDeTermino(termino, null, null, null);
    }

    @Nested
    @DisplayName("alta")
    class Alta {

        @Test
        @DisplayName("guarda termino y forma normalizada, con OTRO, el modo por longitud, activo y el autor del token")
        void porOmision() {
            TerminoProhibido guardado = servicio.agregar(datos("  Spider-Man  "), "mod_ana");

            assertThat(guardado.termino()).isEqualTo("Spider-Man");
            assertThat(guardado.normalizado()).isEqualTo("spiderman");
            assertThat(guardado.categoria()).isEqualTo(CategoriaDeTermino.OTRO);
            assertThat(guardado.modo()).isEqualTo(ModoDeCoincidencia.SUBCADENA);
            assertThat(guardado.activo()).isTrue();
            assertThat(guardado.creadoPor()).isEqualTo("mod_ana");
            assertThat(guardado.creadoEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
            assertThat(guardado.actualizadoEn()).isNull();
            verify(catalogo).invalidar();
        }

        @Test
        @DisplayName("respeta categoria, modo y activo cuando llegan; un termino corto va como PALABRA")
        void explicito() {
            TerminoProhibido marca = servicio.agregar(new ListaNegraAdminService.DatosDeTermino("messi",
                    CategoriaDeTermino.CELEBRIDAD, ModoDeCoincidencia.PALABRA, false), "admin");
            TerminoProhibido corto = servicio.agregar(datos("culo"), "admin");

            assertThat(marca.categoria()).isEqualTo(CategoriaDeTermino.CELEBRIDAD);
            assertThat(marca.modo()).isEqualTo(ModoDeCoincidencia.PALABRA);
            assertThat(marca.activo()).isFalse();
            assertThat(corto.modo()).isEqualTo(ModoDeCoincidencia.PALABRA);
        }

        @Test
        @DisplayName("la misma forma normalizada es el mismo termino: 409 y no se guarda")
        void duplicado() {
            when(repositorio.findByNormalizado("spiderman"))
                    .thenReturn(Optional.of(existente("spiderman", CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA)));

            assertThatThrownBy(() -> servicio.agregar(datos("SP1DER MAN"), "mod"))
                    .isInstanceOf(TerminoDuplicadoException.class)
                    .hasMessageContaining("spiderman");
            verify(repositorio, never()).save(any());
            verify(catalogo, never()).invalidar();
        }

        @Test
        @DisplayName("dos altas simultaneas: la que choca con el indice unico tambien es 409")
        void carrera() {
            when(repositorio.save(any())).thenThrow(new DataIntegrityViolationException("ux_terminos_prohibidos_normalizado"));

            assertThatThrownBy(() -> servicio.agregar(datos("batman"), "mod"))
                    .isInstanceOf(TerminoDuplicadoException.class);
        }

        @Test
        @DisplayName("vacio, de mas de 120 o de menos de 3 letras normalizadas: 400")
        void invalidos() {
            assertThatThrownBy(() -> servicio.agregar(datos("   "), "mod")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> servicio.agregar(datos(null), "mod")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> servicio.agregar(datos("x".repeat(121)), "mod"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("120");
            assertThatThrownBy(() -> servicio.agregar(datos("a-b"), "mod"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("corto");
            assertThatThrownBy(() -> servicio.agregar(datos("!!!"), "mod")).isInstanceOf(IllegalArgumentException.class);
            verify(repositorio, never()).save(any());
        }

        @Test
        @DisplayName("un autor vacio queda nulo y uno larguisimo se recorta a la columna")
        void autor() {
            assertThat(servicio.agregar(datos("batman"), "  ").creadoPor()).isNull();
            assertThat(servicio.agregar(datos("superman"), "a".repeat(150)).creadoPor()).hasSize(100);
        }
    }

    @Nested
    @DisplayName("edicion")
    class Edicion {

        @Test
        @DisplayName("el camino se busca por su forma normalizada; lo omitido se conserva")
        void conservaLoOmitido() {
            TerminoProhibido spiderman = existente("spiderman", CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA);
            when(repositorio.findByNormalizado("spiderman")).thenReturn(Optional.of(spiderman));

            TerminoProhibido editado = servicio.editar("Spider-Man",
                    new ListaNegraAdminService.DatosDeTermino("spiderman", null, null, false), "admin");

            assertThat(editado.activo()).isFalse();
            assertThat(editado.categoria()).isEqualTo(CategoriaDeTermino.MARCA);
            assertThat(editado.modo()).isEqualTo(ModoDeCoincidencia.SUBCADENA);
            assertThat(editado.actualizadoEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
            verify(repositorio).save(spiderman);
            verify(catalogo).invalidar();
        }

        @Test
        @DisplayName("si cambia la forma y no se dice el modo, se recalcula; si se dice, manda")
        void modoAlCambiarLaForma() {
            TerminoProhibido spiderman = existente("spiderman", CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA);
            when(repositorio.findByNormalizado("spiderman")).thenReturn(Optional.of(spiderman));
            when(repositorio.findByNormalizado("spd")).thenReturn(Optional.empty());

            TerminoProhibido corto = servicio.editar("spiderman", datos("SPD"), "admin");
            assertThat(corto.normalizado()).isEqualTo("spd");
            assertThat(corto.modo()).isEqualTo(ModoDeCoincidencia.PALABRA);

            TerminoProhibido marvel = existente("marvel", CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA);
            when(repositorio.findByNormalizado("marvel")).thenReturn(Optional.of(marvel));
            when(repositorio.findByNormalizado("marvelcomics")).thenReturn(Optional.empty());
            TerminoProhibido largo = servicio.editar("marvel", new ListaNegraAdminService.DatosDeTermino(
                    "Marvel Comics", CategoriaDeTermino.MARCA, ModoDeCoincidencia.PALABRA, null), "admin");
            assertThat(largo.modo()).isEqualTo(ModoDeCoincidencia.PALABRA);
        }

        @Test
        @DisplayName("renombrar a una forma que ya tiene otro termino es 409")
        void chocaConOtro() {
            when(repositorio.findByNormalizado("batman"))
                    .thenReturn(Optional.of(existente("batman", CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA)));
            when(repositorio.findByNormalizado("spiderman"))
                    .thenReturn(Optional.of(existente("spiderman", CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA)));

            assertThatThrownBy(() -> servicio.editar("batman", datos("Spider-Man"), "admin"))
                    .isInstanceOf(TerminoDuplicadoException.class);
        }

        @Test
        @DisplayName("editar uno que no existe es 404")
        void noExiste() {
            when(repositorio.findByNormalizado("fantasma")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.editar("fantasma", datos("nuevo"), "admin"))
                    .isInstanceOf(TerminoNoEncontradoException.class);
        }
    }

    @Nested
    @DisplayName("baja")
    class Baja {

        @Test
        @DisplayName("borra el termino buscado por su forma normalizada")
        void borra() {
            TerminoProhibido spiderman = existente("spiderman", CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA);
            when(repositorio.findByNormalizado("spiderman")).thenReturn(Optional.of(spiderman));

            servicio.eliminar("SPIDER-MAN", "admin");

            verify(repositorio).delete(spiderman);
            verify(catalogo).invalidar();
        }

        @Test
        @DisplayName("borrar uno que no existe es 404")
        void noExiste() {
            when(repositorio.findByNormalizado("fantasma")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.eliminar("fantasma", "admin"))
                    .isInstanceOf(TerminoNoEncontradoException.class);
        }
    }

    @Nested
    @DisplayName("listado")
    class Listado {

        @Test
        @DisplayName("pagina fuera de rango o tamano fuera de 1..200: 400")
        void paginaInvalida() {
            assertThatThrownBy(() -> servicio.listar(new ListaNegraAdminService.Filtro(null, null, null, -1, 10)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> servicio.listar(new ListaNegraAdminService.Filtro(null, null, null, 0, 0)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> servicio.listar(new ListaNegraAdminService.Filtro(null, null, null, 0, 201)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pide la pagina ordenada por termino")
        @SuppressWarnings("unchecked")
        void ordenada() {
            ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
            Page<TerminoProhibido> vacia = new PageImpl<>(List.of());
            when(repositorio.findAll(any(Specification.class), pagina.capture())).thenReturn(vacia);

            servicio.listar(new ListaNegraAdminService.Filtro(CategoriaDeTermino.MARCA, true, "spider", 2, 16));

            assertThat(pagina.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pagina.getValue().getPageSize()).isEqualTo(16);
            assertThat(pagina.getValue().getSort().getOrderFor("termino")).isNotNull();
        }
    }
}
