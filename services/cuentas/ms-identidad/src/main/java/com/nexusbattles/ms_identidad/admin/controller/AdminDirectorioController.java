package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.directorio.AuditoriaDeExportaciones;
import com.nexusbattles.ms_identidad.admin.directorio.ConsultaInvalidaException;
import com.nexusbattles.ms_identidad.admin.directorio.CuentasDePrueba;
import com.nexusbattles.ms_identidad.admin.directorio.DirectorioDeCuentas;
import com.nexusbattles.ms_identidad.admin.directorio.ExportacionDelDirectorio;
import com.nexusbattles.ms_identidad.admin.directorio.ExportacionDemasiadoGrandeException;
import com.nexusbattles.ms_identidad.admin.directorio.FiltroDelDirectorio;
import com.nexusbattles.ms_identidad.admin.directorio.IndicadoresDeCuentas;
import com.nexusbattles.ms_identidad.admin.dto.AdminUsuarioDirectorioResponse;
import com.nexusbattles.ms_identidad.admin.dto.IndicadoresDeCuentasResponse;
import com.nexusbattles.ms_identidad.admin.dto.PaginaAdminResponse;
import com.nexusbattles.ms_identidad.auth.codigos.Problemas;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.rbac.security.RequirePermission;
import com.nexusbattles.ms_identidad.rbac.model.Action;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Directorio de jugadores para la consola administrativa.
 *
 * ## Por que es un endpoint y no una consulta del navegador
 *
 * La consola necesita responder «¿quien esta registrado y como esta su
 * cuenta?». La alternativa perezosa seria que el panel pidiera la lista
 * entera y filtrara en el cliente; eso traeria a cada carga todos los
 * usuarios del sistema al navegador, incluidos los correos de gente que el
 * administrador no estaba buscando. Se filtra y se pagina en el servidor.
 *
 * ## Por que no esta en AdminGestionUsuarioController
 *
 * Aquel gestiona UNA cuenta -- editar perfil, suspender, restablecer -- y
 * todo lo suyo muta estado. Esto solo lee y lista. Mezclarlos obligaria a
 * revisar cada cambio futuro preguntandose cual de las dos cosas se esta
 * tocando.
 *
 * ## HU-USR-008 (ms-identidad-admin.yaml 1.3.0)
 *
 * Filtros por rol, estado y fecha de registro, busqueda tambien por el nombre
 * del perfil ({@link FiltroDelDirectorio}), la exportacion del listado con
 * esos mismos filtros ({@link ExportacionDelDirectorio}) y los indicadores de
 * cuentas del panel ({@link IndicadoresDeCuentas}). Todo de solo lectura, como
 * el resto de esta clase.
 *
 * ## HU-USR-009 (ms-identidad-admin.yaml 1.5.0)
 *
 * {@code buscar} tambien encuentra la cuenta por su identificador exacto
 * ({@code uid} o clave {@code id}), y cada exportacion queda en la auditoria
 * ({@link AuditoriaDeExportaciones}): leer no cambia nada, pero sacar datos
 * personales del sistema se registra.
 *
 * Mismo permiso que la gestion de cuentas: quien puede abrir la ficha de un
 * jugador puede buscarla. El guarda es {@code @RequirePermission}, el mismo
 * de siempre; no hay una segunda matriz que se pueda desincronizar.
 */
@RestController
@RequestMapping("/api/v1/admin/jugadores")
public class AdminDirectorioController {

    /** Techo del tamano de pagina. Pedir 10.000 filas no es paginar. */
    private static final int TAMANO_MAXIMO = 100;

    private static final MediaType TEXTO_CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final UsuarioRepository usuarioRepository;
    private final DirectorioDeCuentas directorio;
    private final CuentasDePrueba cuentasDePrueba;
    private final IndicadoresDeCuentas indicadoresDeCuentas;
    private final ExportacionDelDirectorio exportacion;
    private final AuditoriaDeExportaciones auditoriaDeExportaciones;

    public AdminDirectorioController(UsuarioRepository usuarioRepository,
                                     DirectorioDeCuentas directorio,
                                     CuentasDePrueba cuentasDePrueba,
                                     IndicadoresDeCuentas indicadores,
                                     ExportacionDelDirectorio exportacion,
                                     AuditoriaDeExportaciones auditoriaDeExportaciones) {
        this.usuarioRepository = usuarioRepository;
        this.directorio = directorio;
        this.cuentasDePrueba = cuentasDePrueba;
        this.indicadoresDeCuentas = indicadores;
        this.exportacion = exportacion;
        this.auditoriaDeExportaciones = auditoriaDeExportaciones;
    }

    /**
     * @param buscar          texto libre; compara con apodo, correo y (1.3.0)
     *                        nombres y apellidos del perfil; (1.5.0) si es un
     *                        {@code uid} o una clave {@code id}, tambien con la
     *                        cuenta que lo tiene, exacto. Vacio = todos.
     * @param ocultarPruebas  RFINAL-06: excluye, en la consulta y antes de
     *                        paginar, las cuentas de las pruebas automaticas
     *                        ({@link CuentasDePrueba}).
     * @param rol             1.3.0: solo ese rol
     * @param estado          1.3.0: solo ese estado
     * @param registradoDesde 1.3.0: registradas ese dia o despues
     * @param registradoHasta 1.3.0: registradas ese dia o antes
     * @param page            pagina, desde 0
     * @param size            filas por pagina, tope {@value #TAMANO_MAXIMO}
     */
    @GetMapping
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public PaginaAdminResponse<AdminUsuarioDirectorioResponse> listar(
            @RequestParam(name = "buscar", required = false) String buscar,
            @RequestParam(name = "ocultarPruebas", defaultValue = "false") boolean ocultarPruebas,
            @RequestParam(name = "rol", required = false) String rol,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "registradoDesde", required = false) String registradoDesde,
            @RequestParam(name = "registradoHasta", required = false) String registradoHasta,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        FiltroDelDirectorio filtro =
                FiltroDelDirectorio.de(buscar, ocultarPruebas, rol, estado, registradoDesde, registradoHasta);
        int pagina = Math.max(page, 0);
        int tamano = Math.min(Math.max(size, 1), TAMANO_MAXIMO);
        PageRequest pedido = PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id"));

        // Sin ningun filtro, la consulta de siempre. Con cualquiera (tambien el
        // texto, que desde 1.3.0 mira el perfil), la especificacion que los
        // combina; el filtrado y el recuento de la pagina los hace la base.
        // Sin filtro se pasa cadena vacia y no null: un null sin tipo en la
        // consulta hace que PostgreSQL no pueda deducir el tipo del parametro.
        Page<Usuario> resultado = filtro.sinFiltros()
                ? usuarioRepository.buscarParaDirectorio("", pedido)
                : directorio.findAll(filtro.especificacion(cuentasDePrueba), pedido);

        return PaginaAdminResponse.desde(resultado, AdminUsuarioDirectorioResponse::desde);
    }

    /**
     * HU-USR-008 — el listado completo con los filtros vigentes, en CSV.
     * Mismos parametros que {@link #listar} salvo la paginacion.
     *
     * <p>HU-USR-009 (ms-identidad-admin.yaml 1.5.0): cada exportacion hecha
     * queda en la auditoria de ms-cumplimiento —quien, filtros y filas, nunca
     * el contenido—. Si la auditoria no responde, el archivo se entrega igual
     * y el fallo queda en la bitacora ({@link AuditoriaDeExportaciones}). Una
     * exportacion rechazada (400, 422) no produce archivo y no se audita.
     */
    @GetMapping("/exportacion")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<byte[]> exportar(
            @RequestParam(name = "buscar", required = false) String buscar,
            @RequestParam(name = "ocultarPruebas", defaultValue = "false") boolean ocultarPruebas,
            @RequestParam(name = "rol", required = false) String rol,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "registradoDesde", required = false) String registradoDesde,
            @RequestParam(name = "registradoHasta", required = false) String registradoHasta,
            HttpServletRequest peticion) {

        FiltroDelDirectorio filtro =
                FiltroDelDirectorio.de(buscar, ocultarPruebas, rol, estado, registradoDesde, registradoHasta);
        ExportacionDelDirectorio.Exportacion archivo = exportacion.exportar(filtro);
        auditoriaDeExportaciones.registrar(filtro, archivo.filas(),
                (String) peticion.getAttribute("usuarioActual"), uidDe(peticion),
                AdminGestionUsuarioController.obtenerIpReal(peticion));
        return ResponseEntity.ok()
                .contentType(TEXTO_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombreArchivo()).build().toString())
                // Datos personales: ni el navegador ni un intermediario lo guardan.
                .cacheControl(CacheControl.noStore())
                .body(archivo.contenido());
    }

    /**
     * HU-USR-008 — cuentas por estado (ahora) y registros por dia del rango.
     */
    @GetMapping("/indicadores")
    @RequirePermission(Action.GESTIONAR_CUENTAS)
    public ResponseEntity<IndicadoresDeCuentasResponse> indicadores(
            @RequestParam(name = "desde", required = false) String desde,
            @RequestParam(name = "hasta", required = false) String hasta,
            @RequestParam(name = "ocultarPruebas", defaultValue = "false") boolean ocultarPruebas) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(indicadoresDeCuentas.calcular(desde, hasta, ocultarPruebas));
    }

    /** El {@code uid} que deja el interceptor (claim {@code uid} del token), o null. */
    private static String uidDe(HttpServletRequest peticion) {
        Object uid = peticion.getAttribute("uidActual");
        return uid == null ? null : uid.toString();
    }

    /** 400 {@code datos-invalidos}: el filtro no se puede aplicar, y se dice por que. */
    @ExceptionHandler(ConsultaInvalidaException.class)
    public ResponseEntity<ProblemDetail> consultaInvalida(ConsultaInvalidaException error,
                                                          HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.BAD_REQUEST, "datos-invalidos", "Consulta inválida",
                error.getMessage(), peticion.getRequestURI());
    }

    /** 422 {@code exportacion-demasiado-grande}: ningun archivo a medias. */
    @ExceptionHandler(ExportacionDemasiadoGrandeException.class)
    public ResponseEntity<ProblemDetail> exportacionDemasiadoGrande(ExportacionDemasiadoGrandeException error,
                                                                   HttpServletRequest peticion) {
        return Problemas.de(HttpStatus.UNPROCESSABLE_CONTENT, "exportacion-demasiado-grande",
                "Exportación demasiado grande", error.getMessage(), peticion.getRequestURI());
    }
}
