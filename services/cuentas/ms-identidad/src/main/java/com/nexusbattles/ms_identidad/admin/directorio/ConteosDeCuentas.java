package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las dos consultas de los indicadores de cuentas — HU-USR-008 (#561).
 *
 * <p>Por que no van en {@link DirectorioDeCuentas}: un repositorio de
 * especificaciones solo sabe devolver cuentas o contarlas de una en una. Aqui
 * hace falta un recuento AGRUPADO por estado y una proyeccion de una sola
 * columna, y las dos tienen que respetar la misma especificacion que el
 * directorio (las cuentas de pruebas, {@link CuentasDePrueba}). Es JPA estandar
 * (Criteria), sin SQL propio de una base: corre igual en PostgreSQL y en el H2
 * de las pruebas ({@code ConteosDeCuentasTest}).
 *
 * <p>Solo lee. No toca {@code UsuarioRepository}, que comparten el registro,
 * el login y los perfiles.
 */
@Component
public class ConteosDeCuentas {

    private final EntityManager entityManager;

    public ConteosDeCuentas(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Cuantas cuentas hay en cada valor GUARDADO de {@code usuarios.estado}
     * (sin normalizar: las formas anteriores a B2 salen con su nombre y las
     * suma quien publica).
     */
    @Transactional(readOnly = true)
    public Map<String, Long> porEstadoGuardado(Specification<Usuario> especificacion) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> consulta = cb.createTupleQuery();
        Root<Usuario> cuenta = consulta.from(Usuario.class);
        Expression<String> estado = cuenta.get("estado");
        Expression<Long> cuantas = cb.count(cuenta);
        consulta.multiselect(estado, cuantas).groupBy(estado);
        Predicate condicion = especificacion.toPredicate(cuenta, consulta, cb);
        if (condicion != null) {
            consulta.where(condicion);
        }
        Map<String, Long> resultado = new LinkedHashMap<>();
        for (Tuple fila : entityManager.createQuery(consulta).getResultList()) {
            resultado.put(fila.get(estado), fila.get(cuantas));
        }
        return resultado;
    }

    /**
     * El instante de alta de cada cuenta registrada en {@code [desde, hasta)}.
     *
     * <p>Se agrupa por dia en Java y no con una funcion de fecha de la base: no
     * hay una que se escriba igual en PostgreSQL y en H2 desde Criteria, y el
     * volumen esta acotado por el tope del rango (366 dias por omision) y por
     * las altas reales de la plataforma. Se trae una sola columna.
     */
    @Transactional(readOnly = true)
    public List<LocalDateTime> altasEntre(Specification<Usuario> especificacion,
                                          LocalDateTime desde, LocalDateTime hastaExclusivo) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<LocalDateTime> consulta = cb.createQuery(LocalDateTime.class);
        Root<Usuario> cuenta = consulta.from(Usuario.class);
        Path<LocalDateTime> creadoEn = cuenta.get("creadoEn");
        List<Predicate> condiciones = new ArrayList<>();
        condiciones.add(cb.greaterThanOrEqualTo(creadoEn, desde));
        condiciones.add(cb.lessThan(creadoEn, hastaExclusivo));
        Predicate condicion = especificacion.toPredicate(cuenta, consulta, cb);
        if (condicion != null) {
            condiciones.add(condicion);
        }
        consulta.select(creadoEn).where(condiciones.toArray(Predicate[]::new));
        return entityManager.createQuery(consulta).getResultList();
    }
}
