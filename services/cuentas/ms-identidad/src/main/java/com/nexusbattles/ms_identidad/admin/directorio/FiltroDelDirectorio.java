package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Los filtros del directorio administrativo — HU-USR-008 (#561), 7.3.4
 * «Busqueda y filtros de usuarios».
 *
 * <p>Una sola pieza para el listado ({@code GET /admin/jugadores}) y para su
 * exportacion ({@code GET /admin/jugadores/exportacion}): «exportar lo que
 * estoy viendo» es literal porque las dos operaciones arman la consulta con
 * este mismo objeto.
 *
 * <p>Se normaliza al construirse: lo que llega en blanco es «sin filtro», el
 * texto se recorta como siempre, rol y estado se aceptan sin distinguir
 * mayusculas y se guardan como los publica el contrato. Lo que no se puede
 * aplicar se rechaza aqui, con un motivo para la persona
 * ({@link ConsultaInvalidaException}, 400 {@code datos-invalidos}).
 *
 * @param texto           apodo, correo o nombre del perfil, o (HU-USR-009) el
 *                        identificador exacto de la cuenta ({@code uid} o
 *                        clave {@code id}); cadena vacia = sin texto
 * @param ocultarPruebas  sin las cuentas de las pruebas automaticas ({@link CuentasDePrueba})
 * @param rol             nombre del rol del contrato, o null = todos
 * @param estado          estado del contrato ({@link EstadoCuenta#PUBLICADOS}), o null = todos
 * @param registradoDesde primer dia de registro (inclusive), o null
 * @param registradoHasta ultimo dia de registro (inclusive), o null
 */
public record FiltroDelDirectorio(
        String texto,
        boolean ocultarPruebas,
        String rol,
        String estado,
        LocalDate registradoDesde,
        LocalDate registradoHasta) {

    /** Los roles del contrato, en el orden de la Tabla 24. */
    static final List<String> ROLES = Arrays.stream(Role.values()).map(Enum::name).toList();

    public static FiltroDelDirectorio de(String buscar, boolean ocultarPruebas, String rol, String estado,
                                         String registradoDesde, String registradoHasta) {
        String texto = buscar == null ? "" : buscar.trim();
        String rolDelContrato = deLaLista(rol, ROLES, "Ese rol no existe");
        String estadoDelContrato = deLaLista(estado, EstadoCuenta.PUBLICADOS, "Ese estado no existe");
        LocalDate desde = dia(registradoDesde, "registradoDesde");
        LocalDate hasta = dia(registradoHasta, "registradoHasta");
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ConsultaInvalidaException(
                    "«registradoDesde» es posterior a «registradoHasta»: ninguna cuenta puede cumplir las dos.");
        }
        return new FiltroDelDirectorio(texto, ocultarPruebas, rolDelContrato, estadoDelContrato, desde, hasta);
    }

    /**
     * Ningun filtro: ni texto, ni pruebas ocultas, ni rol, ni estado, ni
     * fechas. El directorio responde entonces con la consulta de siempre
     * ({@code UsuarioRepository#buscarParaDirectorio}).
     */
    public boolean sinFiltros() {
        return texto.isEmpty() && !ocultarPruebas && rol == null && estado == null
                && registradoDesde == null && registradoHasta == null;
    }

    /** La consulta del directorio con todos los filtros activos a la vez. */
    public Specification<Usuario> especificacion(CuentasDePrueba cuentasDePrueba) {
        Specification<Usuario> consulta = BusquedaDelDirectorio.buscandoTambienPorNombreEIdentificador(texto);
        if (ocultarPruebas) {
            consulta = consulta.and(cuentasDePrueba.excluidas());
        }
        if (rol != null) {
            consulta = consulta.and(conRol(rol));
        }
        if (estado != null) {
            consulta = consulta.and(enEstado(estado));
        }
        if (registradoDesde != null) {
            consulta = consulta.and(registradaDesde(registradoDesde));
        }
        if (registradoHasta != null) {
            consulta = consulta.and(registradaHasta(registradoHasta));
        }
        return consulta;
    }

    /** Solo las cuentas de ese rol (por su nombre, el del contrato). */
    static Specification<Usuario> conRol(String rol) {
        return (raiz, consulta, cb) -> cb.equal(raiz.get("rol").get("nombre"), rol);
    }

    /** Solo las cuentas en ese estado, en cualquiera de las formas en que se guardo. */
    static Specification<Usuario> enEstado(String estado) {
        return (raiz, consulta, cb) -> raiz.get("estado").in(EstadoCuenta.formasGuardadas(estado));
    }

    /** Registradas ese dia o despues. Una cuenta sin fecha de alta no cumple. */
    static Specification<Usuario> registradaDesde(LocalDate dia) {
        return (raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.<LocalDateTime>get("creadoEn"), dia.atStartOfDay());
    }

    /** Registradas ese dia o antes: antes del primer instante del dia siguiente. */
    static Specification<Usuario> registradaHasta(LocalDate dia) {
        return (raiz, consulta, cb) -> cb.lessThan(raiz.<LocalDateTime>get("creadoEn"), dia.plusDays(1).atStartOfDay());
    }

    /**
     * Un dia del contrato ({@code aaaa-mm-dd}), o null si llega en blanco.
     *
     * @param campo el nombre del parametro, para que el motivo diga cual fallo
     */
    static LocalDate dia(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException noEsUnDia) {
            throw new ConsultaInvalidaException(
                    "La fecha «" + campo + "» no es válida: escríbela como aaaa-mm-dd (por ejemplo, 2026-10-05).");
        }
    }

    private static String deLaLista(String valor, List<String> admitidos, String motivo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        if (!admitidos.contains(normalizado)) {
            throw new ConsultaInvalidaException(motivo + ": usa " + enumeracion(admitidos) + ".");
        }
        return normalizado;
    }

    /** «A, B o C». */
    private static String enumeracion(List<String> valores) {
        if (valores.size() == 1) {
            return valores.get(0);
        }
        return String.join(", ", valores.subList(0, valores.size() - 1)) + " o " + valores.get(valores.size() - 1);
    }
}
