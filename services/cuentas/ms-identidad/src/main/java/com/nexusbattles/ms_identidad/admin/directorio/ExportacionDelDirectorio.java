package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.admin.dto.AdminUsuarioDirectorioResponse;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * El directorio en CSV — HU-USR-008 (#561), 7.3.4 «Exportar listados de
 * usuarios». {@code GET /api/v1/admin/jugadores/exportacion}
 * (ms-identidad-admin.yaml 1.3.0).
 *
 * <p><b>Por que en el servidor y no recorriendo paginas desde el navegador.</b>
 * El directorio ordena por {@code id} descendente: si alguien se registra
 * mientras el navegador pide la pagina 2, todas se corren una fila y el archivo
 * sale con una cuenta repetida o perdida, sin que nadie lo note. Aqui se leen
 * todas las cuentas de los filtros en UNA consulta, con el mismo
 * {@link FiltroDelDirectorio} que el listado, y el permiso es el mismo que el
 * del directorio porque es el mismo controlador.
 *
 * <p><b>Columnas</b>: las que muestra la tabla y ninguna mas — apodo, correo,
 * rol, estado, registro y ultima entrada —, construidas con la misma fila del
 * directorio ({@link AdminUsuarioDirectorioResponse}), sin claves internas ni
 * credenciales.
 *
 * <p><b>Tope</b>: {@code IDENTIDAD_DIRECTORIO_EXPORTACION_MAXIMO_FILAS} (10000).
 * Es tecnico: acota la memoria de una peticion. Por encima no se entrega un
 * archivo recortado, que se leeria como completo: 422 con las dos cifras.
 */
@Component
public class ExportacionDelDirectorio {

    static final String CABECERA = "Apodo,Correo,Rol,Estado,Registro,Última entrada";

    /** Marca de orden de bytes: sin ella una hoja de calculo lee mal las tildes. */
    private static final String BOM = "\uFEFF";
    private static final String FIN_DE_LINEA = "\r\n";
    private static final Sort ORDEN_DEL_DIRECTORIO = Sort.by(Sort.Direction.DESC, "id");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter EN_EL_NOMBRE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final DirectorioDeCuentas directorio;
    private final CuentasDePrueba cuentasDePrueba;
    private final Clock reloj;
    private final int maximoFilas;

    @Autowired
    public ExportacionDelDirectorio(DirectorioDeCuentas directorio, CuentasDePrueba cuentasDePrueba,
                                    @Value("${identidad.directorio.exportacion.maximo-filas:10000}") int maximoFilas) {
        this(directorio, cuentasDePrueba, Clock.systemDefaultZone(), maximoFilas);
    }

    ExportacionDelDirectorio(DirectorioDeCuentas directorio, CuentasDePrueba cuentasDePrueba, Clock reloj,
                             int maximoFilas) {
        if (maximoFilas < 1) {
            throw new IllegalStateException(
                    "identidad.directorio.exportacion.maximo-filas tiene que ser al menos 1 (es " + maximoFilas + ").");
        }
        this.directorio = directorio;
        this.cuentasDePrueba = cuentasDePrueba;
        this.reloj = reloj;
        this.maximoFilas = maximoFilas;
    }

    /** El archivo listo para descargar. */
    public record Exportacion(String nombreArchivo, byte[] contenido) {
    }

    /**
     * @throws ExportacionDemasiadoGrandeException si los filtros dejan mas
     *         cuentas que el tope
     */
    public Exportacion exportar(FiltroDelDirectorio filtro) {
        Page<Usuario> cuentas = directorio.findAll(
                filtro.especificacion(cuentasDePrueba), PageRequest.of(0, maximoFilas, ORDEN_DEL_DIRECTORIO));
        if (cuentas.getTotalElements() > maximoFilas) {
            throw new ExportacionDemasiadoGrandeException(cuentas.getTotalElements(), maximoFilas);
        }
        String nombre = "directorio-de-cuentas-" + EN_EL_NOMBRE.format(LocalDateTime.now(reloj)) + ".csv";
        return new Exportacion(nombre, csv(cuentas.getContent()).getBytes(StandardCharsets.UTF_8));
    }

    static String csv(List<Usuario> cuentas) {
        StringBuilder salida = new StringBuilder(BOM).append(CABECERA).append(FIN_DE_LINEA);
        for (Usuario cuenta : cuentas) {
            AdminUsuarioDirectorioResponse fila = AdminUsuarioDirectorioResponse.desde(cuenta);
            // El estado como lo pinta la tabla: BLOQUEADA si hay un bloqueo
            // temporal vigente; si no, el estado con el nombre del contrato.
            String estado = fila.bloqueada() ? "BLOQUEADA" : EstadoCuenta.normalizado(fila.estado());
            salida.append(celda(fila.apodo())).append(',')
                    .append(celda(fila.email())).append(',')
                    .append(celda(fila.rol())).append(',')
                    .append(celda(estado)).append(',')
                    .append(fecha(fila.creadoEn())).append(',')
                    .append(fecha(fila.ultimoAcceso()))
                    .append(FIN_DE_LINEA);
        }
        return salida.toString();
    }

    /**
     * Una celda de texto: entre comillas, con las comillas dobladas. Si empieza
     * por {@code =}, {@code +}, {@code -}, {@code @}, tabulador o retorno se
     * prefija con un apostrofo, para que una hoja de calculo no la ejecute como
     * formula (inyeccion CSV): el apodo y el correo los escribe gente de fuera.
     * Nulo es una celda vacia.
     */
    static String celda(String texto) {
        if (texto == null) {
            return "";
        }
        String seguro = !texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0 ? "'" + texto : texto;
        return '"' + seguro.replace("\"", "\"\"") + '"';
    }

    /** Fecha y hora del reloj del servicio; vacia si no la hay (nunca una inventada). */
    private static String fecha(LocalDateTime instante) {
        return instante == null ? "" : FECHA.format(instante);
    }
}
