package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.admin.dto.IndicadoresDeCuentasResponse;
import com.nexusbattles.ms_identidad.admin.dto.IndicadoresDeCuentasResponse.RegistrosDelDia;
import com.nexusbattles.ms_identidad.admin.dto.IndicadoresDeCuentasResponse.RegistrosPorDia;
import com.nexusbattles.ms_identidad.auth.model.EstadoCuenta;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Indicadores de cuentas del panel de administracion — HU-USR-008 (#561),
 * 7.3.4 «Panel: vista general». {@code GET /api/v1/admin/jugadores/indicadores}
 * (ms-identidad-admin.yaml 1.3.0).
 *
 * <p>Responde dos preguntas distintas y no las mezcla:
 * <ul>
 *   <li><b>Como esta la comunidad AHORA</b>: cuantas cuentas hay y en que
 *       estado (activas, pendientes de verificar, inactivas, suspendidas,
 *       baneadas). No depende del rango: una cuenta suspendida lo esta hoy.</li>
 *   <li><b>Cuantas se registraron cada dia</b> de un rango, por la fecha de
 *       alta de la cuenta, con los dias sin altas a 0: un hueco en la serie se
 *       leeria como «sin datos», y no es lo mismo.</li>
 * </ul>
 *
 * <p>Los dos topes son tecnicos, no reglas de negocio, y van por
 * configuracion: cuantos dias se muestran si no se pide rango
 * ({@code IDENTIDAD_INDICADORES_DIAS_POR_OMISION}, 30) y el rango mas largo
 * que se admite ({@code IDENTIDAD_INDICADORES_DIAS_MAXIMOS}, 366): acotan el
 * tamano de la serie y de la consulta. El dia es el del reloj del servicio.
 *
 * <p>Las «alertas de comportamiento sospechoso» de la ficha NO estan aqui:
 * ningun documento fija su umbral (observacion de la ficha, D-25) y este
 * servicio no inventa uno.
 */
@Service
public class IndicadoresDeCuentas {

    private final ConteosDeCuentas conteos;
    private final CuentasDePrueba cuentasDePrueba;
    private final Clock reloj;
    private final int diasPorOmision;
    private final int diasMaximos;

    @Autowired
    public IndicadoresDeCuentas(ConteosDeCuentas conteos, CuentasDePrueba cuentasDePrueba,
                                @Value("${identidad.indicadores.dias-por-omision:30}") int diasPorOmision,
                                @Value("${identidad.indicadores.dias-maximos:366}") int diasMaximos) {
        this(conteos, cuentasDePrueba, Clock.systemDefaultZone(), diasPorOmision, diasMaximos);
    }

    IndicadoresDeCuentas(ConteosDeCuentas conteos, CuentasDePrueba cuentasDePrueba, Clock reloj,
                         int diasPorOmision, int diasMaximos) {
        if (diasPorOmision < 1 || diasMaximos < diasPorOmision) {
            throw new IllegalStateException("Configuracion imposible de los indicadores de cuentas: "
                    + "dias-por-omision=" + diasPorOmision + " y dias-maximos=" + diasMaximos
                    + " (hace falta 1 <= dias-por-omision <= dias-maximos).");
        }
        this.conteos = conteos;
        this.cuentasDePrueba = cuentasDePrueba;
        this.reloj = reloj;
        this.diasPorOmision = diasPorOmision;
        this.diasMaximos = diasMaximos;
    }

    /**
     * @param desde          primer dia ({@code aaaa-mm-dd}) o null
     * @param hasta          ultimo dia ({@code aaaa-mm-dd}) o null
     * @param ocultarPruebas sin las cuentas de pruebas automaticas (criterio del directorio)
     * @throws ConsultaInvalidaException fecha mal escrita, desde posterior a hasta o rango mas largo que el tope
     */
    public IndicadoresDeCuentasResponse calcular(String desde, String hasta, boolean ocultarPruebas) {
        LocalDate inicioPedido = FiltroDelDirectorio.dia(desde, "desde");
        LocalDate finPedido = FiltroDelDirectorio.dia(hasta, "hasta");
        LocalDate fin = finPedido != null ? finPedido : LocalDate.now(reloj);
        LocalDate inicio = inicioPedido != null ? inicioPedido : fin.minusDays(diasPorOmision - 1L);
        if (inicio.isAfter(fin)) {
            throw new ConsultaInvalidaException("«desde» (" + inicio + ") es posterior a «hasta» (" + fin + ").");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fin) + 1;
        if (dias > diasMaximos) {
            throw new ConsultaInvalidaException("El rango tiene " + dias + " días y se admiten como máximo "
                    + diasMaximos + ": acórtalo.");
        }

        // La misma especificacion para las dos consultas: el total y la serie
        // cuentan exactamente las mismas cuentas.
        Specification<Usuario> cuentas = ocultarPruebas ? cuentasDePrueba.excluidas() : Specification.unrestricted();

        Map<String, Long> porEstado = porEstado(conteos.porEstadoGuardado(cuentas));
        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();

        List<LocalDateTime> altas = conteos.altasEntre(cuentas, inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay());
        Map<LocalDate, Long> altasPorDia = altas.stream()
                .collect(Collectors.groupingBy(LocalDateTime::toLocalDate, Collectors.counting()));
        List<RegistrosDelDia> serie = inicio.datesUntil(fin.plusDays(1))
                .map(dia -> new RegistrosDelDia(dia, altasPorDia.getOrDefault(dia, 0L)))
                .toList();
        long registros = serie.stream().mapToLong(RegistrosDelDia::cuentas).sum();

        return new IndicadoresDeCuentasResponse(total, porEstado,
                new RegistrosPorDia(inicio, fin, registros, serie), ocultarPruebas, OffsetDateTime.now(reloj));
    }

    /**
     * Los cinco estados del contrato siempre, en su orden y con 0 si no hay
     * cuentas; las formas anteriores a B2 suman en el suyo. Un valor guardado
     * que no es del contrato no deberia existir: si existe, se publica con su
     * nombre en vez de desaparecer del total.
     */
    private static Map<String, Long> porEstado(Map<String, Long> guardados) {
        Map<String, Long> porEstado = EstadoCuenta.PUBLICADOS.stream()
                .collect(Collectors.toMap(Function.identity(), estado -> 0L, Long::sum, LinkedHashMap::new));
        guardados.forEach((estado, cuantas) -> porEstado.merge(
                estado == null ? "SIN_ESTADO" : EstadoCuenta.normalizado(estado),
                cuantas == null ? 0L : cuantas, Long::sum));
        return porEstado;
    }
}
