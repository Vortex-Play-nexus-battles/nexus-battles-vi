package com.nexusbattles.ms_identidad.perfiles.service;

import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.perfiles.dto.PerfilPublicoResponse;
import com.nexusbattles.ms_identidad.perfiles.repository.PerfilUsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * B6 — buscar a un jugador por el principio de su apodo para escribirle un
 * mensaje privado (feedback del profesor; ms-identidad-perfiles.yaml,
 * {@code GET /api/v1/perfiles/publicos}).
 *
 * <p>Cada regla del contrato tiene su porque:
 * <ul>
 *   <li><b>Minimo {@value #MINIMO_CARACTERES} caracteres.</b> Con una o dos
 *       letras coincide media base y la busqueda se convierte en un listado
 *       de jugadores por entregas. Tres ya acotan a quien se esta buscando.</li>
 *   <li><b>Maximo {@value #MAXIMO_CARACTERES}.</b> Es lo que mide un apodo
 *       ({@code usuarios.apodo} es {@code VARCHAR(50)}, el mismo tope que el
 *       registro): un texto mas largo no puede ser el principio de ninguno.</li>
 *   <li><b>Como mucho {@value #MAXIMO_RESULTADOS} resultados.</b> Es para
 *       elegir destinatario, no un directorio: si no aparece, se escribe una
 *       letra mas.</li>
 *   <li><b>Solo cuentas ACTIVO.</b> A una cuenta pendiente de verificar,
 *       inactiva, suspendida o baneada no se le puede escribir de forma util,
 *       y ofrecerla revelaria su estado a cualquiera.</li>
 *   <li><b>{@code %} y {@code _} literales.</b> En un {@code LIKE} son
 *       comodines: sin escaparlos, «{@code ___}» o «{@code %%%}» devolverian
 *       jugadores cualesquiera y el minimo de tres caracteres no protegeria
 *       nada.</li>
 * </ul>
 * La longitud se mide en caracteres de verdad (puntos de codigo), como la
 * cuenta PostgreSQL en el {@code VARCHAR}, no en unidades UTF-16 de Java.
 */
@Service
public class BusquedaDeJugadores {

    public static final int MINIMO_CARACTERES = 3;
    public static final int MAXIMO_CARACTERES = 50;
    public static final int MAXIMO_RESULTADOS = 10;

    private static final char ESCAPE = PerfilUsuarioRepository.ESCAPE_LIKE;

    private final PerfilUsuarioRepository perfiles;

    public BusquedaDeJugadores(PerfilUsuarioRepository perfiles) {
        this.perfiles = perfiles;
    }

    /**
     * Jugadores ACTIVO cuyo apodo empieza por {@code texto}, sin distinguir
     * mayusculas, ordenados por apodo: solo {@code uid}, apodo y avatar.
     *
     * @throws BusquedaInvalidaException si el texto, sin los espacios de
     *         alrededor, tiene menos de {@value #MINIMO_CARACTERES} caracteres
     *         o mas de {@value #MAXIMO_CARACTERES}
     */
    public List<PerfilPublicoResponse> buscar(String texto) {
        // trim(), como el registro al guardar el apodo: se compara con lo que se guardo.
        String prefijo = texto == null ? "" : texto.trim();
        int caracteres = prefijo.codePointCount(0, prefijo.length());
        if (caracteres < MINIMO_CARACTERES) {
            throw new BusquedaInvalidaException(
                    "Escribe al menos " + MINIMO_CARACTERES + " caracteres del apodo.");
        }
        if (caracteres > MAXIMO_CARACTERES) {
            throw new BusquedaInvalidaException(
                    "Un apodo tiene como máximo " + MAXIMO_CARACTERES + " caracteres.");
        }
        return perfiles.buscarPublicosPorPrefijo(
                comoPrefijo(prefijo), EstadoCuenta.ACTIVO, PageRequest.of(0, MAXIMO_RESULTADOS));
    }

    /** {@code a%b} -> {@code a!%b%}: el texto tal cual, seguido del unico comodin. */
    private static String comoPrefijo(String texto) {
        StringBuilder patron = new StringBuilder(texto.length() + 8);
        for (int i = 0; i < texto.length(); i++) {
            char caracter = texto.charAt(i);
            if (caracter == ESCAPE || caracter == '%' || caracter == '_') {
                patron.append(ESCAPE);
            }
            patron.append(caracter);
        }
        return patron.append('%').toString();
    }
}
