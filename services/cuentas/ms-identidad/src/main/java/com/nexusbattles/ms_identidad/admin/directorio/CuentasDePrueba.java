package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Que es una cuenta de las pruebas automaticas — RFINAL-06.
 *
 * <p><b>El problema.</b> En la revision del super administrador en AWS DEV
 * (4-oct) las primeras veinte filas del directorio eran cuentas {@code qa_},
 * {@code smoke_} y {@code canario_} creadas por las pruebas automaticas. No se
 * borra nada: el directorio puede ocultarlas, en la consulta y antes de
 * paginar ({@code GET /admin/jugadores?ocultarPruebas=true},
 * ms-identidad-admin.yaml 1.2.0).
 *
 * <p><b>De donde sale el criterio.</b> No se invento: es como las crean las
 * pruebas del repositorio.
 * <ul>
 *   <li><b>Dominio de correo {@code nexus.test}.</b> Todas las cuentas que
 *       crean las pruebas lo usan: {@code tests/e2e/ayudantes/cuentas.js}
 *       ({@code sesionDe}: {@code <apodo>@nexus.test}), la semilla del banco
 *       ({@code tests/e2e/sembrar.sh}), el smoke de AWS, los canarios, la
 *       prueba del profesor, el contrato del profesor, la activacion R18 y el
 *       laboratorio visual ({@code tests/visual/identidad.js},
 *       {@code personas.js}). {@code .test} es un dominio reservado
 *       (RFC 6761): una persona no puede recibir correo en el.</li>
 *   <li><b>Prefijos de apodo {@code qa_}, {@code smoke_} y {@code canario_}.</b>
 *       Los de las tres familias que corren contra DEV: {@code smoke_<marca>}
 *       (smoke-aws), {@code canario_<marca>} (canarios-jugador) y
 *       {@code qa_prof_…}, {@code qa_fb…}, {@code qa_visual_…},
 *       {@code qa_jugador_…}, {@code qa_moderador_…} (profesor,
 *       regresion-feedback, laboratorio visual). Son los que vio el super
 *       administrador en DEV.</li>
 * </ul>
 * Una cuenta es de pruebas si cumple cualquiera de las dos cosas, sin
 * distinguir mayusculas. Lo de las pruebas que creen otro tipo de cuenta se
 * anade aqui, en la configuracion, no en el codigo.
 *
 * <p><b>Un solo sitio configurable.</b> Las dos listas son
 * {@code identidad.directorio.pruebas.dominios} y
 * {@code identidad.directorio.pruebas.prefijos-apodo} (application.properties,
 * variables {@code IDENTIDAD_DIRECTORIO_PRUEBAS_DOMINIOS} e
 * {@code IDENTIDAD_DIRECTORIO_PRUEBAS_PREFIJOS}), separadas por comas. Vacias,
 * no se oculta nada. El valor por omision del {@code @Value} repite el del
 * archivo, como el resto del servicio: las pruebas corren con su propio
 * application.properties, que oculta el principal.
 */
@Component
public class CuentasDePrueba {

    /**
     * Caracter de escape de los {@code LIKE}. No es la barra invertida por la
     * misma razon que {@code PerfilUsuarioRepository#ESCAPE_LIKE}: no significa
     * nada ni en Java, ni en JPQL, ni en SQL. Sin escapar, el {@code _} de
     * {@code qa_} seria un comodin y «qabot» pasaria por una cuenta de pruebas.
     */
    static final char ESCAPE = '!';

    private final List<String> dominios;
    private final List<String> prefijos;

    public CuentasDePrueba(
            @Value("${identidad.directorio.pruebas.dominios:nexus.test}") String dominios,
            @Value("${identidad.directorio.pruebas.prefijos-apodo:qa_,smoke_,canario_}") String prefijos) {
        this.dominios = lista(dominios).stream()
                .map(dominio -> dominio.startsWith("@") ? dominio.substring(1) : dominio)
                .filter(dominio -> !dominio.isEmpty())
                .toList();
        this.prefijos = lista(prefijos);
    }

    /** Dominios reservados para pruebas, en minusculas y sin la arroba. */
    public List<String> dominios() {
        return dominios;
    }

    /** Prefijos de apodo de las pruebas, en minusculas. */
    public List<String> prefijos() {
        return prefijos;
    }

    /** Patrones {@code LIKE} sobre el correo en minusculas: {@code %@dominio}. */
    List<String> patronesDeCorreo() {
        return dominios.stream().map(dominio -> "%@" + escapar(dominio)).toList();
    }

    /** Patrones {@code LIKE} sobre el apodo en minusculas: {@code prefijo%}. */
    List<String> patronesDeApodo() {
        return prefijos.stream().map(prefijo -> escapar(prefijo) + "%").toList();
    }

    /**
     * Las cuentas que NO son de pruebas: ni su correo termina en un dominio de
     * pruebas ni su apodo empieza por un prefijo de pruebas. Sin listas
     * configuradas, deja pasar todas.
     */
    public Specification<Usuario> excluidas() {
        List<String> correos = patronesDeCorreo();
        List<String> apodos = patronesDeApodo();
        return (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            Expression<String> correo = cb.lower(raiz.get("email"));
            for (String patron : correos) {
                condiciones.add(cb.notLike(correo, patron, ESCAPE));
            }
            Expression<String> apodo = cb.lower(raiz.get("apodo"));
            for (String patron : apodos) {
                condiciones.add(cb.notLike(apodo, patron, ESCAPE));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    /** Escapa los comodines del {@code LIKE} y el propio caracter de escape. */
    static String escapar(String literal) {
        StringBuilder salida = new StringBuilder(literal.length() + 4);
        for (char c : literal.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                salida.append(ESCAPE);
            }
            salida.append(c);
        }
        return salida.toString();
    }

    private static List<String> lista(String valor) {
        if (valor == null || valor.isBlank()) {
            return List.of();
        }
        return Arrays.stream(valor.split(","))
                .map(String::trim)
                .filter(elemento -> !elemento.isEmpty())
                .map(elemento -> elemento.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
