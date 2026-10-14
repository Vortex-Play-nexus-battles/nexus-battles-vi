package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.admin.dto.CuentasBloqueadasResponse;
import com.nexusbattles.ms_identidad.admin.dto.CuentasBloqueadasResponse.CuentaBloqueada;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Cuentas bloqueadas por intentos fallidos en un periodo — SA-20, §7.3.4
 * «Alertas de comportamiento sospechoso». {@code GET /api/v1/admin/jugadores/bloqueos}
 * (ms-identidad-admin.yaml 1.11.0).
 *
 * <p>Es la señal 1 de D-45, que decidio el PO el 2026-10-06: «una cuenta
 * bloqueada por intentos fallidos de login», con la regla que ya existe
 * ({@link com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService});
 * aqui no se introduce ningun numero nuevo. La señal 2 (varias sanciones sin
 * revertir) la publica moderacion-sanciones, dueña de las sanciones.
 *
 * <p>Lee la marca {@code ultimoBloqueoEn} (V9), que a diferencia de
 * {@code bloqueadoHasta} no se borra al volver a entrar. El periodo empieza en
 * {@code desde} (por omision, los ultimos
 * {@code IDENTIDAD_BLOQUEOS_DIAS_POR_OMISION} dias, 7) y llega hasta ahora; un
 * periodo mas largo que el de los indicadores responde 400. Los dos topes son
 * tecnicos, no reglas de negocio.
 */
@Service
public class CuentasBloqueadasPorIntentos {

    /** Techo de la lista: la alerta enseña las mas recientes y el total real. */
    public static final int LIMITE_MAXIMO = 100;

    private final DirectorioDeCuentas directorio;
    private final CuentasDePrueba cuentasDePrueba;
    private final Clock reloj;
    private final int diasPorOmision;
    private final int diasMaximos;

    @Autowired
    public CuentasBloqueadasPorIntentos(DirectorioDeCuentas directorio, CuentasDePrueba cuentasDePrueba,
                                        @Value("${identidad.bloqueos.dias-por-omision:7}") int diasPorOmision,
                                        @Value("${identidad.indicadores.dias-maximos:366}") int diasMaximos) {
        this(directorio, cuentasDePrueba, Clock.systemDefaultZone(), diasPorOmision, diasMaximos);
    }

    CuentasBloqueadasPorIntentos(DirectorioDeCuentas directorio, CuentasDePrueba cuentasDePrueba, Clock reloj,
                                 int diasPorOmision, int diasMaximos) {
        if (diasPorOmision < 1 || diasMaximos < diasPorOmision) {
            throw new IllegalStateException("Configuracion imposible de las cuentas bloqueadas: "
                    + "dias-por-omision=" + diasPorOmision + " y dias-maximos=" + diasMaximos
                    + " (hace falta 1 <= dias-por-omision <= dias-maximos).");
        }
        this.directorio = directorio;
        this.cuentasDePrueba = cuentasDePrueba;
        this.reloj = reloj;
        this.diasPorOmision = diasPorOmision;
        this.diasMaximos = diasMaximos;
    }

    /**
     * @param desde          primer dia ({@code aaaa-mm-dd}) o null
     * @param ocultarPruebas sin las cuentas de pruebas automaticas (criterio del directorio)
     * @param limite         cuantas devolver, de 1 a {@value #LIMITE_MAXIMO}; null = el maximo
     * @throws ConsultaInvalidaException fecha mal escrita o futura, periodo demasiado largo o limite fuera de rango
     */
    public CuentasBloqueadasResponse consultar(String desde, boolean ocultarPruebas, Integer limite) {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate pedido = FiltroDelDirectorio.dia(desde, "desde");
        LocalDate inicio = pedido != null ? pedido : hoy.minusDays(diasPorOmision - 1L);
        if (inicio.isAfter(hoy)) {
            throw new ConsultaInvalidaException("«desde» (" + inicio + ") es posterior a hoy (" + hoy + ").");
        }
        long dias = ChronoUnit.DAYS.between(inicio, hoy) + 1;
        if (dias > diasMaximos) {
            throw new ConsultaInvalidaException("El periodo tiene " + dias + " días y se admiten como máximo "
                    + diasMaximos + ": acórtalo.");
        }
        int tope = limite == null ? LIMITE_MAXIMO : limite;
        if (tope < 1 || tope > LIMITE_MAXIMO) {
            throw new ConsultaInvalidaException("«limite» va de 1 a " + LIMITE_MAXIMO + ".");
        }

        LocalDateTime comienzo = inicio.atStartOfDay();
        Specification<Usuario> bloqueadas = (raiz, consulta, cb) ->
                cb.greaterThanOrEqualTo(raiz.<LocalDateTime>get("ultimoBloqueoEn"), comienzo);
        Specification<Usuario> criterio = ocultarPruebas ? bloqueadas.and(cuentasDePrueba.excluidas()) : bloqueadas;

        // El bloqueo mas reciente primero; el id desempata para que el orden
        // sea estable entre dos consultas.
        Sort orden = Sort.by(Sort.Order.desc("ultimoBloqueoEn"), Sort.Order.desc("id"));
        Page<Usuario> pagina = directorio.findAll(criterio, PageRequest.of(0, tope, orden));

        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<CuentaBloqueada> cuentas = pagina.getContent().stream()
                .map(usuario -> CuentaBloqueada.de(usuario, ahora, reloj.getZone()))
                .toList();
        return new CuentasBloqueadasResponse(inicio, pagina.getTotalElements(), cuentas, ocultarPruebas,
                OffsetDateTime.now(reloj));
    }
}
