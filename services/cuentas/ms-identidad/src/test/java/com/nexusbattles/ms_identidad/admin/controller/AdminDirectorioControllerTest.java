package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.directorio.AuditoriaDeExportaciones;
import com.nexusbattles.ms_identidad.admin.directorio.ConsultaInvalidaException;
import com.nexusbattles.ms_identidad.admin.directorio.CuentasDePrueba;
import com.nexusbattles.ms_identidad.admin.directorio.DirectorioDeCuentas;
import com.nexusbattles.ms_identidad.admin.directorio.ExportacionDelDirectorio;
import com.nexusbattles.ms_identidad.admin.directorio.IndicadoresDeCuentas;
import com.nexusbattles.ms_identidad.admin.dto.AdminUsuarioDirectorioResponse;
import com.nexusbattles.ms_identidad.admin.dto.PaginaAdminResponse;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El directorio administrativo de jugadores.
 *
 * Lo que se prueba aqui no es "devuelve una lista": es que el servidor manda
 * sobre la consulta. Quien llama puede pedir la pagina -3 con 10.000 filas y
 * un filtro de espacios en blanco, y lo que llegue al repositorio tiene que
 * seguir siendo una peticion sana. Y que la fila que sale no lleve
 * credenciales, que es la unica parte de esto que, si falla, no se nota
 * hasta que ya es tarde.
 */
@ExtendWith(MockitoExtension.class)
class AdminDirectorioControllerTest {

    /** Sin filtro es cadena vacia, nunca null: un null sin tipo rompe la consulta. */
    private static final String SIN_FILTRO = "";

    /** El criterio por omision de application.properties (RFINAL-06). */
    private static final CuentasDePrueba CRITERIO = new CuentasDePrueba("nexus.test", "qa_,smoke_,canario_");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private DirectorioDeCuentas directorio;

    @Mock
    private IndicadoresDeCuentas indicadores;

    @Mock
    private ExportacionDelDirectorio exportacion;

    @Mock
    private AuditoriaDeExportaciones auditoriaDeExportaciones;

    private AdminDirectorioController controlador() {
        return new AdminDirectorioController(usuarioRepository, directorio, CRITERIO, indicadores, exportacion,
                auditoriaDeExportaciones);
    }

    /** El listado sin los filtros de 1.3.0 (rol, estado, fechas): la llamada de siempre. */
    private static PaginaAdminResponse<AdminUsuarioDirectorioResponse> listar(
            AdminDirectorioController controlador, String buscar, boolean ocultarPruebas, int page, int size) {
        return controlador.listar(buscar, ocultarPruebas, null, null, null, null, page, size);
    }

    private static Usuario usuario(String apodo, String email, String rol) {
        Usuario usuario = new Usuario();
        usuario.setPublicId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        usuario.setApodo(apodo);
        usuario.setEmail(email);
        usuario.setEstado("ACTIVO");
        usuario.setPassword("hash-que-nunca-deberia-salir");
        if (rol != null) {
            usuario.setRol(new RolEntity(rol, "rol de prueba"));
        }
        return usuario;
    }

    private void devolviendo(Page<Usuario> pagina) {
        when(usuarioRepository.buscarParaDirectorio(any(), any(Pageable.class))).thenReturn(pagina);
    }

    private Pageable pageableUsado() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(usuarioRepository).buscarParaDirectorio(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void sinFiltroConsultaTodoYUsaLaPaginaPorDefecto() {
        devolviendo(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        AdminDirectorioController controlador = controlador();

        PaginaAdminResponse<AdminUsuarioDirectorioResponse> respuesta = listar(controlador, null, false, 0, 20);

        verify(usuarioRepository).buscarParaDirectorio(eq(SIN_FILTRO), any(Pageable.class));
        Pageable usado = pageableUsado();
        assertEquals(0, usado.getPageNumber());
        assertEquals(20, usado.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "id"), usado.getSort(),
                "el directorio muestra primero las cuentas mas nuevas");
        assertTrue(respuesta.contenido().isEmpty());
    }

    /** Un filtro de solo espacios es "no hay filtro", no una busqueda de espacios. */
    @Test
    void elFiltroEnBlancoEquivaleAConsultarTodo() {
        devolviendo(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        AdminDirectorioController controlador = controlador();

        listar(controlador, "   ", false, 0, 20);

        verify(usuarioRepository).buscarParaDirectorio(eq(SIN_FILTRO), any(Pageable.class));
    }

    /**
     * HU-USR-008 (1.3.0) — con texto la busqueda mira tambien el nombre del
     * perfil, asi que va por la especificacion y no por la consulta de siempre.
     * Que el texto llegue recortado lo prueba {@code FiltroDelDirectorioTest}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void conTextoBuscaTambienPorNombreConLaEspecificacion() {
        when(directorio.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        listar(controlador(), "  Ana  ", false, 0, 20);

        verify(directorio).findAll(any(Specification.class), any(Pageable.class));
        verify(usuarioRepository, never()).buscarParaDirectorio(any(), any(Pageable.class));
    }

    /**
     * Pedir 10.000 filas no es paginar. El techo lo pone el servidor porque
     * es el unico que paga el coste de la consulta.
     */
    @Test
    void recortaElTamanoDePaginaAlTecho() {
        devolviendo(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));
        AdminDirectorioController controlador = controlador();

        listar(controlador, null, false, 0, 10000);

        assertEquals(100, pageableUsado().getPageSize());
    }

    @Test
    void normalizaPaginaNegativaYTamanoCero() {
        devolviendo(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));
        AdminDirectorioController controlador = controlador();

        listar(controlador, null, false, -3, 0);

        Pageable usado = pageableUsado();
        assertEquals(0, usado.getPageNumber(), "una pagina negativa no existe; se atiende la primera");
        assertEquals(1, usado.getPageSize(), "cero filas por pagina seria una consulta sin respuesta");
    }

    /**
     * La fila lleva lo que la tabla muestra y nada mas. Si algun dia alguien
     * anade la contrasena al record, esta prueba es la que lo detiene.
     */
    @Test
    void laFilaNoExponeCredenciales() {
        Usuario jugador = usuario("Ana", "ana@nexus.test", "JUGADOR");
        jugador.setCreadoEn(LocalDateTime.of(2026, 1, 5, 10, 0));
        jugador.setUltimoAcceso(LocalDateTime.of(2026, 9, 20, 18, 30));
        devolviendo(new PageImpl<>(List.of(jugador), PageRequest.of(0, 20), 1));
        AdminDirectorioController controlador = controlador();

        AdminUsuarioDirectorioResponse fila = listar(controlador, null, false, 0, 20).contenido().get(0);

        assertEquals("Ana", fila.apodo());
        assertEquals("ana@nexus.test", fila.email());
        assertEquals("ACTIVO", fila.estado());
        assertEquals("JUGADOR", fila.rol());
        assertEquals(LocalDateTime.of(2026, 1, 5, 10, 0), fila.creadoEn());
        assertEquals(LocalDateTime.of(2026, 9, 20, 18, 30), fila.ultimoAcceso());
        assertEquals(jugador.getPublicId(), fila.uid());
        assertFalse(
                Arrays.stream(AdminUsuarioDirectorioResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .map(nombre -> nombre.toLowerCase())
                        .anyMatch(nombre -> nombre.contains("password")
                                || nombre.contains("clave")
                                || nombre.contains("hash")
                                || nombre.contains("token")),
                "el directorio no puede publicar credenciales de nadie");
    }

    @Test
    void trasladaLosContadoresDeLaPagina() {
        devolviendo(new PageImpl<>(List.of(usuario("Ana", "ana@nexus.test", "JUGADOR")),
                PageRequest.of(1, 5), 11));
        AdminDirectorioController controlador = controlador();

        PaginaAdminResponse<AdminUsuarioDirectorioResponse> respuesta = listar(controlador, null, false, 1, 5);

        assertEquals(1, respuesta.pagina());
        assertEquals(5, respuesta.tamano());
        assertEquals(11, respuesta.total());
        assertEquals(3, respuesta.totalPaginas());
        assertEquals(1, respuesta.contenido().size());
    }

    /** Bloqueo vigente por intentos fallidos: la consola tiene que verlo. */
    @Test
    void marcaBloqueadaLaCuentaConBloqueoVigente() {
        Usuario jugador = usuario("Beto", "beto@nexus.test", "JUGADOR");
        jugador.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        jugador.setSuspendidoHasta(LocalDateTime.of(2026, 12, 31, 0, 0));
        devolviendo(new PageImpl<>(List.of(jugador), PageRequest.of(0, 20), 1));
        AdminDirectorioController controlador = controlador();

        AdminUsuarioDirectorioResponse fila = listar(controlador, null, false, 0, 20).contenido().get(0);

        assertTrue(fila.bloqueada());
        assertEquals(LocalDateTime.of(2026, 12, 31, 0, 0), fila.suspendidoHasta());
    }

    /** Un bloqueo que ya vencio no bloquea: la fecha esta, el estado no. */
    @Test
    void unBloqueoVencidoNoCuentaComoBloqueo() {
        Usuario jugador = usuario("Cris", "cris@nexus.test", "JUGADOR");
        jugador.setBloqueadoHasta(LocalDateTime.now().minusMinutes(1));
        devolviendo(new PageImpl<>(List.of(jugador), PageRequest.of(0, 20), 1));
        AdminDirectorioController controlador = controlador();

        assertFalse(listar(controlador, null, false, 0, 20).contenido().get(0).bloqueada());
    }

    /**
     * RFINAL-06 / HU-USR-008 — sin ningun filtro (ni texto, ni
     * {@code ocultarPruebas}, ni los de 1.3.0) el directorio responde con la
     * misma consulta de siempre: quien no manda nada no nota nada.
     */
    @Test
    void sinNingunFiltroLaConsultaEsLaDeSiempre() {
        devolviendo(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        listar(controlador(), null, false, 0, 20);

        verify(usuarioRepository).buscarParaDirectorio(eq(SIN_FILTRO), any(Pageable.class));
        verifyNoInteractions(directorio);
    }

    /**
     * HU-USR-008 — rol, estado y fechas van en la misma consulta, antes de
     * paginar: la pagina, el tamano y el orden no cambian, y el total que llega
     * es el de las cuentas que cumplen.
     */
    @Test
    @SuppressWarnings("unchecked")
    void losFiltrosNuevosVanEnLaConsultaAntesDePaginar() {
        when(directorio.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(usuario("Ana", "ana@ejemplo.org", "MODERADOR")),
                        PageRequest.of(1, 20), 21));

        PaginaAdminResponse<AdminUsuarioDirectorioResponse> respuesta = controlador()
                .listar(null, false, "MODERADOR", "ACTIVO", "2026-09-01", "2026-09-30", 1, 20);

        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(directorio).findAll(any(Specification.class), pedido.capture());
        assertEquals(PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "id")), pedido.getValue());
        verify(usuarioRepository, never()).buscarParaDirectorio(any(), any(Pageable.class));
        assertEquals(21, respuesta.total());
    }

    /** Un filtro que no se puede aplicar no llega a la base: lo rechaza el filtro. */
    @Test
    void unFiltroInvalidoNoConsultaNada() {
        AdminDirectorioController controlador = controlador();

        assertThrows(ConsultaInvalidaException.class,
                () -> controlador.listar(null, false, "PIRATA", null, null, null, 0, 20));

        verifyNoInteractions(usuarioRepository, directorio);
    }

    /**
     * RFINAL-06 — con {@code ocultarPruebas} las cuentas de las pruebas se
     * excluyen en la consulta: la pagina, el tamano y el orden son los mismos,
     * y el total que llega es el de las que quedan, no el de la tabla.
     */
    @Test
    @SuppressWarnings("unchecked")
    void ocultarPruebasExcluyeEnLaConsultaAntesDePaginar() {
        Usuario real = usuario("Ana", "ana@ejemplo.org", "JUGADOR");
        when(directorio.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(real), PageRequest.of(2, 20), 41));

        PaginaAdminResponse<AdminUsuarioDirectorioResponse> respuesta =
                listar(controlador(), "  Ana  ", true, 2, 20);

        ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
        verify(directorio).findAll(any(Specification.class), pedido.capture());
        assertEquals(2, pedido.getValue().getPageNumber());
        assertEquals(20, pedido.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "id"), pedido.getValue().getSort());
        verify(usuarioRepository, never()).buscarParaDirectorio(any(), any(Pageable.class));
        assertEquals(41, respuesta.total());
        assertEquals("Ana", respuesta.contenido().get(0).apodo());
    }

    /**
     * RFINAL-06 — la fila lleva la clave de la cuenta: la que esperan la ficha
     * de gestion y el cambio de rol. Sin ella no se podia abrir la ficha de una
     * cuenta desde el directorio sin conocer un numero que nadie veia.
     */
    @Test
    void laFilaLlevaLaClaveDeLaCuenta() {
        Usuario jugador = usuario("Ana", "ana@ejemplo.org", "JUGADOR");
        jugador.setId(42L);
        devolviendo(new PageImpl<>(List.of(jugador), PageRequest.of(0, 20), 1));

        AdminUsuarioDirectorioResponse fila = listar(controlador(), null, false, 0, 20).contenido().get(0);

        assertEquals(42L, fila.id());
    }

    /**
     * Rol nulo no deberia pasar -- la columna es NOT NULL -- pero el
     * directorio es una pantalla de diagnostico: si una fila esta mal, tiene
     * que poder mostrarla, no reventar la tabla entera.
     */
    @Test
    void unaCuentaSinRolNiFechasSeMuestraIgual() {
        Usuario jugador = usuario("SinRol", "sinrol@nexus.test", null);
        jugador.setCreadoEn(null);
        devolviendo(new PageImpl<>(List.of(jugador), PageRequest.of(0, 20), 1));
        AdminDirectorioController controlador = controlador();

        AdminUsuarioDirectorioResponse fila = listar(controlador, null, false, 0, 20).contenido().get(0);

        assertNull(fila.rol());
        assertNull(fila.creadoEn());
        assertNull(fila.ultimoAcceso(), "nulo significa que nunca ha entrado, y se muestra tal cual");
        assertNull(fila.suspendidoHasta());
        assertFalse(fila.bloqueada());
    }
}
