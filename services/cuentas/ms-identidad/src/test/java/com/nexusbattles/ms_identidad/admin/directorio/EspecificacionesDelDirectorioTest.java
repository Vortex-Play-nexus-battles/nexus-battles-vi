package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que las especificaciones del directorio le piden a JPA — RFINAL-06.
 *
 * <p>Sin base de datos: se comprueba que cada patron llega a la columna que
 * toca, en minusculas y con su caracter de escape. Que la consulta resultante
 * devuelva las filas correctas lo comprueba {@code DirectorioDeCuentasTest}
 * contra H2 en la integracion continua.
 */
@DisplayName("Especificaciones del directorio")
@SuppressWarnings("unchecked")
class EspecificacionesDelDirectorioTest {

    private final Root<Usuario> raiz = mock(Root.class);
    private final CriteriaQuery<?> consulta = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final Path<Object> email = mock(Path.class);
    private final Path<Object> apodo = mock(Path.class);
    private final Expression<String> emailEnMinusculas = mock(Expression.class);
    private final Expression<String> apodoEnMinusculas = mock(Expression.class);
    private final Predicate resultado = mock(Predicate.class);

    @BeforeEach
    void columnas() {
        when(raiz.get("email")).thenReturn(email);
        when(raiz.get("apodo")).thenReturn(apodo);
        when(cb.lower((Expression<String>) (Expression<?>) email)).thenReturn(emailEnMinusculas);
        when(cb.lower((Expression<String>) (Expression<?>) apodo)).thenReturn(apodoEnMinusculas);
    }

    @Test
    @DisplayName("excluir: el correo no termina en el dominio y el apodo no empieza por el prefijo")
    void excluirNiegaCadaPatronEnSuColumna() {
        when(cb.and(any(Predicate[].class))).thenReturn(resultado);
        CuentasDePrueba criterio = new CuentasDePrueba("nexus.test", "qa_,smoke_");

        Predicate predicado = criterio.excluidas().toPredicate(raiz, consulta, cb);

        assertSame(resultado, predicado);
        verify(cb).notLike(emailEnMinusculas, "%@nexus.test", '!');
        verify(cb).notLike(apodoEnMinusculas, "qa!_%", '!');
        verify(cb).notLike(apodoEnMinusculas, "smoke!_%", '!');
    }

    @Test
    @DisplayName("excluir sin listas configuradas no anade ninguna condicion")
    void excluirSinListasNoFiltra() {
        when(cb.and(any(Predicate[].class))).thenReturn(resultado);

        new CuentasDePrueba("", "").excluidas().toPredicate(raiz, consulta, cb);

        verify(cb, never()).notLike(any(Expression.class), anyString(), anyChar());
        verify(cb).and(new Predicate[0]);
    }

    @Test
    @DisplayName("buscar: el texto en minusculas, en el apodo o en el correo")
    void buscarComparaApodoYCorreo() {
        Predicate porApodo = mock(Predicate.class);
        Predicate porCorreo = mock(Predicate.class);
        when(cb.like(apodoEnMinusculas, "%ana%")).thenReturn(porApodo);
        when(cb.like(emailEnMinusculas, "%ana%")).thenReturn(porCorreo);
        when(cb.or(porApodo, porCorreo)).thenReturn(resultado);

        Predicate predicado = BusquedaDelDirectorio.buscando("AnA").toPredicate(raiz, consulta, cb);

        assertSame(resultado, predicado);
    }

    @Test
    @DisplayName("buscar sin texto no filtra")
    void buscarSinTextoNoFiltra() {
        when(cb.conjunction()).thenReturn(resultado);

        assertSame(resultado, BusquedaDelDirectorio.buscando("").toPredicate(raiz, consulta, cb));
        verify(cb, never()).like(any(Expression.class), anyString());
    }
}
