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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    // ------------------------------------------------------------ candidata

    // La candidata copia TODOS los temas de produccion conservando su clave
    // (y desde 1.6.0 sus intenciones adicionales), y toma el siguiente numero.
    @Test
    void crearCandidata_copiaLosTemasDeProduccionConSuMismaClaveYSusIntenciones() {
        UUID idProduccion = UUID.randomUUID();
        VersionBaseConocimiento produccion = mock(VersionBaseConocimiento.class);
        when(produccion.getId()).thenReturn(idProduccion);
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.of(produccion));
        when(versionRepository.existsByEstado(EstadoVersion.BORRADOR)).thenReturn(false);
        when(versionRepository.buscarNumeroMaximo()).thenReturn(3);
        when(versionRepository.saveAndFlush(any(VersionBaseConocimiento.class))).thenAnswer(inv -> inv.getArgument(0));
        TemaConocimiento registro = tema(produccion, "clave-registro", "Como crear una cuenta");
        registro.asignarIntencionesAdicionales(List.of(Categoria.SOPORTE_TECNICO));
        when(temaRepository.findByVersionIdOrderByTituloAsc(idProduccion)).thenReturn(List.of(registro));

        VersionBaseConocimiento candidata = servicio.crearCandidata("Ajustes de septiembre");

        assertThat(candidata.getNumero()).isEqualTo(4);
        assertThat(candidata.getEstado()).isEqualTo(EstadoVersion.BORRADOR);
        List<TemaConocimiento> copias = capturarGuardados();
        assertThat(copias).singleElement().satisfies(copia -> {
            assertThat(copia.getClave()).isEqualTo("clave-registro");
            assertThat(copia.getVersion()).isSameAs(candidata);
            assertThat(copia.getIntencionesAdicionales()).containsExactly(Categoria.SOPORTE_TECNICO);
        });
    }

    @Test
    void crearCandidata_siYaHayUna_lanza409() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION))
            .thenReturn(Optional.of(mock(VersionBaseConocimiento.class)));
        when(versionRepository.existsByEstado(EstadoVersion.BORRADOR)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crearCandidata(null))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(versionRepository, never()).saveAndFlush(any());
    }

    // Dos administradores a la vez: el indice parcial de V4 frena al segundo.
    @Test
    void crearCandidata_siOtroLaCreoAlMismoTiempo_lanza409() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION))
            .thenReturn(Optional.of(mock(VersionBaseConocimiento.class)));
        when(versionRepository.saveAndFlush(any(VersionBaseConocimiento.class)))
            .thenThrow(new DataIntegrityViolationException("uk_versiones_un_borrador"));

        assertThatThrownBy(() -> servicio.crearCandidata(null))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getReason()).contains("Ya existe una version candidata");
            });
        verify(temaRepository, never()).saveAll(any());
    }

    @Test
    void crearCandidata_sinProduccion_lanza409() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.PRODUCCION)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crearCandidata(null))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // 1.6.0: mientras esperaba el bloqueo, otro despliegue o una reversion
    // cambiaron la produccion: no se crea la candidata desde la vieja.
    @Test
    void crearCandidata_siProduccionCambioMientrasEsperaba_lanzaCambioConcurrente() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.PRODUCCION)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.PRODUCCION)).thenReturn(true);

        assertThatThrownBy(() -> servicio.crearCandidata(null)).isInstanceOf(CambioConcurrenteException.class);
        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test
    void descartarCandidata_laBloqueaYBorraSusTemasYLaVersion() {
        VersionBaseConocimiento candidata = candidataExistente();

        servicio.descartarCandidata();

        InOrder orden = inOrder(versionRepository, temaRepository);
        orden.verify(versionRepository).bloquearLaDeEstado(EstadoVersion.BORRADOR);
        orden.verify(temaRepository).deleteByVersionId(candidata.getId());
        orden.verify(versionRepository).delete(candidata);
    }

    @Test
    void descartarCandidata_sinCandidata_lanza409() {
        sinCandidata();

        assertThatThrownBy(() -> servicio.descartarCandidata())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(versionRepository, never()).delete(any());
    }

    // Listar es de solo lectura: no bloquea (PostgreSQL no bloquea filas en
    // una transaccion de solo lectura).
    @Test
    void listarTemasDeCandidata_noBloqueaNada() {
        VersionBaseConocimiento candidata = conId(VersionBaseConocimiento.nuevaCandidata(2, null));
        when(versionRepository.findByEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        when(temaRepository.findByVersionIdOrderByTituloAsc(candidata.getId()))
            .thenReturn(List.of(tema(candidata, "a", "A")));

        assertThat(servicio.listarTemasDeCandidata()).hasSize(1);
        verify(versionRepository, never()).bloquearLaDeEstado(any());
    }

    @Test
    void listarTemasDeCandidata_sinCandidata_lanza409() {
        when(versionRepository.findByEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.listarTemasDeCandidata())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---------------------------------------------------------------- temas

    @Test
    void agregarTema_sinCandidata_lanza409() {
        sinCandidata();

        assertThatThrownBy(() -> servicio.agregarTema(solicitud(List.of("como me registro"))))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // 1.6.0: si la candidata se desplego o descarto mientras se esperaba y
    // ya hay otra, el cambio no cae en la que no se estaba viendo.
    @Test
    void agregarTema_siLaCandidataCambioMientrasEsperaba_lanzaCambioConcurrente() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.BORRADOR)).thenReturn(true);

        assertThatThrownBy(() -> servicio.agregarTema(solicitud(List.of("como me registro"))))
            .isInstanceOf(CambioConcurrenteException.class);
        verify(temaRepository, never()).save(any());
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
        assertThat(tema.getIntencionesAdicionales()).isEmpty(); // null = ninguna
    }

    // 1.6.0: la misma variante dos veces (sin contar tildes ni mayusculas) se
    // guarda una vez, sin error, con la forma de su primera aparicion.
    @Test
    void agregarTema_conVariantesRepetidas_lasGuardaUnaVez() {
        candidataExistente();
        when(temaRepository.save(any(TemaConocimiento.class))).thenAnswer(inv -> inv.getArgument(0));

        TemaConocimiento tema = servicio.agregarTema(conVariantesEn(
            solicitud(List.of("Cómo me registro", "como me REGISTRO", "crear cuenta", "crear cuenta ")),
            List.of("sign up", "Sign up!")));

        assertThat(tema.getPalabrasClaveEs()).isEqualTo("Cómo me registro, crear cuenta");
        assertThat(tema.getPalabrasClaveEn()).isEqualTo("sign up");
    }

    // 1.6.0 (7.4.10): las intenciones adicionales se guardan en el orden del enum.
    @Test
    void agregarTema_conIntencionesAdicionales_lasGuardaOrdenadas() {
        candidataExistente();
        when(temaRepository.save(any(TemaConocimiento.class))).thenAnswer(inv -> inv.getArgument(0));

        TemaConocimiento tema = servicio.agregarTema(conIntenciones(solicitud(List.of("como me registro")),
            Categoria.SOPORTE_TECNICO, Categoria.PRODUCTO));

        assertThat(tema.getIntencionesAdicionales()).containsExactly(Categoria.PRODUCTO, Categoria.SOPORTE_TECNICO);
        assertThat(tema.atiende(Categoria.CUENTA_Y_REGISTRO)).isTrue();
        assertThat(tema.atiende(Categoria.PRODUCTO)).isTrue();
        assertThat(tema.atiende(Categoria.FAQ_GENERAL)).isFalse();
    }

    @Test
    void agregarTema_conMasDeCincoIntenciones_lanza400SinTocarLaBase() {
        TemaConocimientoRequest seis = conIntenciones(solicitud(List.of("registro")), Categoria.PRODUCTO,
            Categoria.MECANICA_JUEGO, Categoria.MODALIDAD_JUEGO, Categoria.SUBASTA_Y_COMERCIO,
            Categoria.SOPORTE_TECNICO, Categoria.FAQ_GENERAL);

        assertThatThrownBy(() -> servicio.agregarTema(seis))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).startsWith("Un tema atiende como maximo 5 intenciones").contains("6");
            });
        verify(versionRepository, never()).bloquearLaDeEstado(any());
    }

    @Test
    void agregarTema_conUnaIntencionRepetida_lanza400() {
        TemaConocimientoRequest repetida = conIntenciones(solicitud(List.of("registro")), Categoria.PRODUCTO,
            Categoria.PRODUCTO);

        assertThatThrownBy(() -> servicio.agregarTema(repetida))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).isEqualTo("La intencion PRODUCTO esta repetida en las intenciones adicionales.");
            });
    }

    @Test
    void agregarTema_conLaPrincipalComoAdicional_lanza400() {
        TemaConocimientoRequest principal = conIntenciones(solicitud(List.of("registro")),
            Categoria.CUENTA_Y_REGISTRO);

        assertThatThrownBy(() -> servicio.agregarTema(principal))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).contains("CUENTA_Y_REGISTRO ya es la categoria principal del tema");
            });
    }

    @Test
    void agregarTema_conUnaIntencionVacia_lanza400() {
        TemaConocimientoRequest vacia = conIntenciones(solicitud(List.of("registro")), Categoria.PRODUCTO, null);

        assertThatThrownBy(() -> servicio.agregarTema(vacia))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).contains("viene vacia");
            });
    }

    // Una variante con coma se partiria en dos al leerla el motor.
    @Test
    void agregarTema_conVarianteConComa_lanza400() {
        assertThatThrownBy(() -> servicio.agregarTema(solicitud(List.of("registro, cuenta"))))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(temaRepository, never()).save(any());
    }

    // 1.6.0 (RF-CHA-013 «entrada duplicada»): el mismo titulo sin tildes,
    // mayusculas ni signos que otro tema de la candidata es 409 TEMA_DUPLICADO.
    @Test
    void agregarTema_conElTituloDeOtroTema_lanzaTemaDuplicado() {
        VersionBaseConocimiento candidata = candidataExistente();
        when(temaRepository.findByVersionIdOrderByTituloAsc(candidata.getId()))
            .thenReturn(List.of(tema(candidata, "clave-otra", "Cómo crear una cuenta")));

        assertThatThrownBy(() -> servicio.agregarTema(conTitulo(solicitud(List.of("registro")),
            " ¿COMO crear una CUENTA? ")))
            .isInstanceOf(TemaDuplicadoException.class)
            .hasMessageContaining("'¿COMO crear una CUENTA?'")
            .hasMessageContaining("'Cómo crear una cuenta'");
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

    // Editar un tema sin cambiarle el titulo no choca consigo mismo; sus
    // intenciones se reemplazan.
    @Test
    void editarTema_conSuMismoTitulo_loActualizaConSusIntenciones() {
        VersionBaseConocimiento candidata = candidataExistente();
        TemaConocimiento existente = conId(tema(candidata, "clave-registro", "Como crear una cuenta"));
        existente.asignarIntencionesAdicionales(List.of(Categoria.PRODUCTO));
        when(temaRepository.findByIdAndVersionId(existente.getId(), candidata.getId()))
            .thenReturn(Optional.of(existente));
        when(temaRepository.findByVersionIdOrderByTituloAsc(candidata.getId())).thenReturn(List.of(existente));

        TemaConocimiento editado = servicio.editarTema(existente.getId(), conIntenciones(
            solicitud(List.of("como me registro", "Como me registro")), Categoria.FAQ_GENERAL));

        assertThat(editado).isSameAs(existente);
        assertThat(editado.getClave()).isEqualTo("clave-registro");
        assertThat(editado.getIntencionesAdicionales()).containsExactly(Categoria.FAQ_GENERAL);
        assertThat(editado.getPalabrasClaveEs()).isEqualTo("como me registro");
        assertThat(editado.getTipoRespuesta()).isEqualTo(TipoRespuesta.PASO_A_PASO);
    }

    @Test
    void editarTema_conElTituloDeOtroTema_lanzaTemaDuplicado() {
        VersionBaseConocimiento candidata = candidataExistente();
        TemaConocimiento editado = conId(tema(candidata, "clave-registro", "Como crear una cuenta"));
        TemaConocimiento otro = conId(tema(candidata, "clave-pujas", "Como pujar"));
        when(temaRepository.findByIdAndVersionId(editado.getId(), candidata.getId())).thenReturn(Optional.of(editado));
        when(temaRepository.findByVersionIdOrderByTituloAsc(candidata.getId())).thenReturn(List.of(editado, otro));

        assertThatThrownBy(() -> servicio.editarTema(editado.getId(),
            conTitulo(solicitud(List.of("pujar")), "como PUJAR")))
            .isInstanceOf(TemaDuplicadoException.class);
        assertThat(editado.getTitulo()).isEqualTo("Como crear una cuenta");
    }

    @Test
    void eliminarTema_borraElTemaDeLaCandidata() {
        VersionBaseConocimiento candidata = candidataExistente();
        TemaConocimiento existente = conId(tema(candidata, "clave-registro", "Como crear una cuenta"));
        when(temaRepository.findByIdAndVersionId(existente.getId(), candidata.getId()))
            .thenReturn(Optional.of(existente));

        servicio.eliminarTema(existente.getId());

        verify(temaRepository).delete(existente);
    }

    // ------------------------------------------------ exportar / importar

    @Test
    void exportar_incluyeLasIntencionesAdicionales() {
        VersionBaseConocimiento version = conId(VersionBaseConocimiento.nuevaCandidata(7, null));
        TemaConocimiento conIntencion = tema(version, "clave-registro", "Como crear una cuenta");
        conIntencion.asignarIntencionesAdicionales(List.of(Categoria.SOPORTE_TECNICO));
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(temaRepository.findByVersionIdOrderByTituloAsc(version.getId())).thenReturn(List.of(conIntencion));

        ExportacionBaseConocimiento exportacion = servicio.exportar(version.getId());

        assertThat(exportacion.version()).isEqualTo(7);
        assertThat(exportacion.temas()).singleElement()
            .satisfies(t -> assertThat(t.intencionesAdicionales()).containsExactly(Categoria.SOPORTE_TECNICO));
    }

    @Test
    void exportar_versionInexistente_lanza404() {
        UUID inexistente = UUID.randomUUID();
        when(versionRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.exportar(inexistente))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void importar_conClavesRepetidas_lanza400SinBorrarNada() {
        List<TemaIntercambio> archivo = List.of(intercambio("clave-a", "Tema a"), intercambio("clave-a", "Tema b"));

        assertThatThrownBy(() -> servicio.importarEnCandidata(archivo))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(temaRepository, never()).deleteByVersionId(any());
    }

    // 1.6.0: como la importacion reemplaza la candidata entera, el duplicado
    // solo puede estar dentro del archivo. Se dice cuales y no se toca nada.
    @Test
    void importar_conTitulosRepetidosEnElArchivo_lanzaTemaDuplicadoSinBorrarNada() {
        List<TemaIntercambio> archivo = List.of(intercambio("clave-a", "Cómo pujar"),
            intercambio("clave-b", "Otra cosa"), intercambio("clave-c", "como PUJAR?"));

        assertThatThrownBy(() -> servicio.importarEnCandidata(archivo))
            .isInstanceOf(TemaDuplicadoException.class)
            .hasMessageStartingWith("Los temas 1 y 3 del archivo tienen el mismo titulo");
        verify(versionRepository, never()).bloquearLaDeEstado(any());
        verify(temaRepository, never()).deleteByVersionId(any());
    }

    // 1.6.0: mas de 500 temas, 413 sin mirar ni bloquear nada.
    @Test
    void importar_conMasDeQuinientosTemas_lanzaImportacionDemasiadoGrande() {
        List<TemaIntercambio> archivo = IntStream.rangeClosed(1, ImportacionDemasiadoGrandeException.MAXIMO_DE_TEMAS + 1)
            .mapToObj(i -> intercambio("clave-" + i, "Tema " + i))
            .toList();

        assertThatThrownBy(() -> servicio.importarEnCandidata(archivo))
            .isInstanceOf(ImportacionDemasiadoGrandeException.class)
            .hasMessageContaining("501 temas")
            .hasMessageContaining("como maximo 500");
        verify(versionRepository, never()).bloquearLaDeEstado(any());
        verify(temaRepository, never()).deleteByVersionId(any());
    }

    // Exactamente 500 si entran.
    @Test
    void importar_conQuinientosTemas_losImporta() {
        candidataExistente();
        when(temaRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        List<TemaIntercambio> archivo = IntStream.rangeClosed(1, ImportacionDemasiadoGrandeException.MAXIMO_DE_TEMAS)
            .mapToObj(i -> intercambio("clave-" + i, "Tema " + i))
            .toList();

        assertThat(servicio.importarEnCandidata(archivo)).hasSize(500);
    }

    @Test
    void importar_conIntencionesInvalidas_lanza400QueDiceQueTema() {
        List<TemaIntercambio> archivo = List.of(intercambio("clave-a", "Tema a"),
            conIntenciones(intercambio("clave-b", "Tema b"), Categoria.FAQ_GENERAL));

        assertThatThrownBy(() -> servicio.importarEnCandidata(archivo))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).startsWith("Tema 2 del archivo ('Tema b'): la intencion FAQ_GENERAL");
            });
        verify(temaRepository, never()).deleteByVersionId(any());
    }

    // Importar reemplaza el contenido: bloquea la candidata, borra, hace flush
    // (para que el borrado vaya antes que las inserciones) y guarda,
    // respetando las claves y las intenciones que trae el archivo y generando
    // las claves que faltan. Un archivo de antes de 1.6.0 (sin intenciones)
    // entra igual.
    @Test
    void importar_reemplazaLaCandidataRespetandoClavesEIntenciones() {
        VersionBaseConocimiento candidata = candidataExistente();
        when(temaRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TemaConocimiento> importados = servicio.importarEnCandidata(List.of(
            conIntenciones(intercambio("clave-a", "Tema a"), Categoria.SOPORTE_TECNICO, Categoria.PRODUCTO),
            intercambio(null, "Tema sin clave")));

        InOrder orden = inOrder(versionRepository, temaRepository);
        orden.verify(versionRepository).bloquearLaDeEstado(EstadoVersion.BORRADOR);
        orden.verify(temaRepository).deleteByVersionId(candidata.getId());
        orden.verify(temaRepository).flush();
        orden.verify(temaRepository).saveAll(any());
        assertThat(importados).hasSize(2);
        assertThat(importados.get(0).getClave()).isEqualTo("clave-a");
        assertThat(importados.get(0).getIntencionesAdicionales())
            .containsExactly(Categoria.PRODUCTO, Categoria.SOPORTE_TECNICO);
        assertThat(importados.get(1).getClave()).isNotBlank();
        assertThat(importados.get(1).getIntencionesAdicionales()).isEmpty();
    }

    @Test
    void importar_sinCandidata_lanza409SinBorrarNada() {
        sinCandidata();

        assertThatThrownBy(() -> servicio.importarEnCandidata(List.of(intercambio("clave-a", "Tema a"))))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(temaRepository, never()).deleteByVersionId(any());
    }

    // ------------------------------------------------------------- ayudas

    private VersionBaseConocimiento candidataExistente() {
        VersionBaseConocimiento candidata = mock(VersionBaseConocimiento.class);
        // lenient: agregarTema no consulta el id de la candidata, pero
        // editarTema e importarEnCandidata sí. Sin esto, Mockito estricto
        // falla con UnnecessaryStubbingException en las pruebas que no lo usan.
        lenient().when(candidata.getId()).thenReturn(UUID.randomUUID());
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.of(candidata));
        return candidata;
    }

    private void sinCandidata() {
        when(versionRepository.bloquearLaDeEstado(EstadoVersion.BORRADOR)).thenReturn(Optional.empty());
        when(versionRepository.existsByEstado(EstadoVersion.BORRADOR)).thenReturn(false);
    }

    @SuppressWarnings("unchecked")
    private List<TemaConocimiento> capturarGuardados() {
        ArgumentCaptor<List<TemaConocimiento>> captor = ArgumentCaptor.forClass(List.class);
        verify(temaRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // El id lo genera JPA al guardar; en una prueba unitaria se asigna a mano.
    private static <T> T conId(T entidad) {
        ReflectionTestUtils.setField(entidad, "id", UUID.randomUUID());
        return entidad;
    }

    private static TemaConocimientoRequest solicitud(List<String> variantesEs) {
        return new TemaConocimientoRequest(Categoria.CUENTA_Y_REGISTRO, null, TipoRespuesta.PASO_A_PASO,
            "Como crear una cuenta", variantesEs, null, "Para crear tu cuenta...", null, 0, null);
    }

    private static TemaConocimientoRequest conIntenciones(TemaConocimientoRequest base, Categoria... intenciones) {
        return new TemaConocimientoRequest(base.categoria(), new ArrayList<>(Arrays.asList(intenciones)),
            base.tipoRespuesta(), base.titulo(), base.variantesEs(), base.variantesEn(), base.respuestaEs(),
            base.respuestaEn(), base.prioridad(), base.activo());
    }

    private static TemaConocimientoRequest conTitulo(TemaConocimientoRequest base, String titulo) {
        return new TemaConocimientoRequest(base.categoria(), base.intencionesAdicionales(), base.tipoRespuesta(),
            titulo, base.variantesEs(), base.variantesEn(), base.respuestaEs(), base.respuestaEn(), base.prioridad(),
            base.activo());
    }

    private static TemaConocimientoRequest conVariantesEn(TemaConocimientoRequest base, List<String> variantesEn) {
        return new TemaConocimientoRequest(base.categoria(), base.intencionesAdicionales(), base.tipoRespuesta(),
            base.titulo(), base.variantesEs(), variantesEn, base.respuestaEs(), base.respuestaEn(), base.prioridad(),
            base.activo());
    }

    private static TemaIntercambio intercambio(String clave, String titulo) {
        return new TemaIntercambio(clave, Categoria.FAQ_GENERAL, null, TipoRespuesta.DIRECTA, titulo,
            List.of("pregunta de prueba"), null, "Respuesta de prueba", null, 0, true);
    }

    private static TemaIntercambio conIntenciones(TemaIntercambio base, Categoria... intenciones) {
        return new TemaIntercambio(base.clave(), base.categoria(), List.of(intenciones), base.tipoRespuesta(),
            base.titulo(), base.variantesEs(), base.variantesEn(), base.respuestaEs(), base.respuestaEn(),
            base.prioridad(), base.activo());
    }

    private static TemaConocimiento tema(VersionBaseConocimiento version, String clave, String titulo) {
        return new TemaConocimiento(version, clave, Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.PASO_A_PASO,
            titulo, "como me registro", null, "Para crear tu cuenta...", null, 0, true);
    }
}
