package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.VersionBaseConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.VersionBaseConocimientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BaseConocimientoAdminServiceTest {

    @Mock
    private VersionBaseConocimientoRepository versionRepository;
    @Mock
    private TemaConocimientoRepository temaRepository;

    private BaseConocimientoAdminService servicio;

    @BeforeEach
    void configurar() {
        servicio = new BaseConocimientoAdminService(versionRepository, temaRepository);
    }

    // La candidata copia TODOS los temas de produccion conservando su clave,
    // y toma el siguiente numero de version.
    @Test
    void crearCandidata_copiaLosTemasDeProduccionConSuMismaClave() {
        UUID idProduccion = UUID.randomUUID();
        VersionBaseConocimiento produccion = mock(VersionBaseConocimiento.class);
        when(produccion.getId()).thenReturn(idProduccion);
        when(versionRepository.findByEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());
        when(versionRepository.findByEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(versionRepository.buscarNumeroMaximo()).thenReturn(3);
        when(versionRepository.saveAndFlush(any(VersionBaseConocimiento.class))).thenAnswer(inv -> inv.getArgument(0));
        when(temaRepository.findByVersionIdOrderByTituloAsc(idProduccion))
            .thenReturn(List.of(tema(produccion, "clave-registro")));

        VersionBaseConocimiento candidata = servicio.crearCandidata("Ajustes de septiembre");

        assertThat(candidata.getNumero()).isEqualTo(4);
        assertThat(candidata.getEstado()).isEqualTo(EstadoVersion.BORRADOR);
        List<TemaConocimiento> copias = capturarGuardados();
        assertThat(copias).singleElement().satisfies(copia -> {
            assertThat(copia.getClave()).isEqualTo("clave-registro");
            assertThat(copia.getVersion()).isSameAs(candidata);
        });
    }

    @Test
    void crearCandidata_siYaHayUna_lanza409() {
        when(versionRepository.findByEstado(EstadoVersion.BORRADOR))
            .thenReturn(Optional.of(mock(VersionBaseConocimiento.class)));

        assertThatThrownBy(() -> servicio.crearCandidata(null))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test
    void agregarTema_sinCandidata_lanza409() {
        when(versionRepository.findByEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.agregarTema(solicitud(List.of("como me registro"))))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void agregarTema_generaUnaClaveNuevaYUneLasVariantes() {
        VersionBaseConocimiento candidata = candidataExistente();
        when(temaRepository.save(any(TemaConocimiento.class))).thenAnswer(inv -> inv.getArgument(0));

        TemaConocimiento tema = servicio.agregarTema(solicitud(List.of("como me registro", "crear cuenta")));

        assertThat(tema.getClave()).isNotBlank();
        assertThat(tema.getVersion()).isSameAs(candidata);
        assertThat(tema.getPalabrasClaveEs()).isEqualTo("como me registro, crear cuenta");
        assertThat(tema.isActivo()).isTrue(); // activo null = true
    }

    // Una variante con coma se partiria en dos al leerla el motor.
    @Test
    void agregarTema_conVarianteConComa_lanza400() {
        assertThatThrownBy(() -> servicio.agregarTema(solicitud(List.of("registro, cuenta"))))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(temaRepository, never()).save(any());
    }

    // Un tema que no es de la candidata (produccion, retirada o inexistente)
    // no se puede editar: 404.
    @Test
    void editarTema_queNoEsDeLaCandidata_lanza404() {
        VersionBaseConocimiento candidata = candidataExistente();
        UUID temaDeProduccion = UUID.randomUUID();
        when(temaRepository.findByIdAndVersionId(temaDeProduccion, candidata.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.editarTema(temaDeProduccion, solicitud(List.of("registro"))))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void importar_conClavesRepetidas_lanza400SinBorrarNada() {
        List<TemaIntercambio> archivo = List.of(intercambio("clave-a"), intercambio("clave-a"));

        assertThatThrownBy(() -> servicio.importarEnCandidata(archivo))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(temaRepository, never()).deleteByVersionId(any());
    }

    // Importar reemplaza el contenido: borra, hace flush (para que el borrado
    // vaya antes que las inserciones) y guarda, respetando las claves que trae
    // el archivo y generando las que faltan.
    @Test
    void importar_reemplazaLaCandidataRespetandoLasClaves() {
        VersionBaseConocimiento candidata = candidataExistente();
        when(temaRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TemaConocimiento> importados = servicio.importarEnCandidata(
            List.of(intercambio("clave-a"), intercambio(null)));

        InOrder orden = inOrder(temaRepository);
        orden.verify(temaRepository).deleteByVersionId(candidata.getId());
        orden.verify(temaRepository).flush();
        orden.verify(temaRepository).saveAll(any());
        assertThat(importados).hasSize(2);
        assertThat(importados.get(0).getClave()).isEqualTo("clave-a");
        assertThat(importados.get(1).getClave()).isNotBlank();
    }

    private VersionBaseConocimiento candidataExistente() {
        VersionBaseConocimiento candidata = mock(VersionBaseConocimiento.class);
        // lenient: agregarTema no consulta el id de la candidata, pero
        // editarTema e importarEnCandidata sí. Sin esto, Mockito estricto
        // falla con UnnecessaryStubbingException en las pruebas que no lo usan.
        lenient().when(candidata.getId()).thenReturn(UUID.randomUUID());
        when(versionRepository.findByEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        return candidata;
    }

    @SuppressWarnings("unchecked")
    private List<TemaConocimiento> capturarGuardados() {
        ArgumentCaptor<List<TemaConocimiento>> captor = ArgumentCaptor.forClass(List.class);
        verify(temaRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static TemaConocimientoRequest solicitud(List<String> variantesEs) {
        return new TemaConocimientoRequest(Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.PASO_A_PASO,
            "Como crear una cuenta", variantesEs, null, "Para crear tu cuenta...", null, 0, null);
    }

    private static TemaIntercambio intercambio(String clave) {
        return new TemaIntercambio(clave, Categoria.FAQ_GENERAL, TipoRespuesta.DIRECTA, "Tema " + clave,
            List.of("pregunta de prueba"), null, "Respuesta de prueba", null, 0, true);
    }

    private static TemaConocimiento tema(VersionBaseConocimiento version, String clave) {
        return new TemaConocimiento(version, clave, Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.PASO_A_PASO,
            "Como crear una cuenta", "como me registro", null, "Para crear tu cuenta...", null, 0, true);
    }
}
