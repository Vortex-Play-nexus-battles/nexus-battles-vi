package com.nexusbattles.ms_identidad.admin.directorio;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-USR-009 (#562) — que texto es un identificador de cuenta y contra que
 * columna se compara. La consulta de verdad la prueba
 * {@code FiltrosDelDirectorioEnBaseTest}; aqui, sin base, que la regla no
 * convierte texto arbitrario y nunca lanza.
 */
@DisplayName("Busqueda por identificador en el directorio (HU-USR-009)")
class BusquedaPorIdentificadorTest {

    @SuppressWarnings("unchecked")
    private final Root<Usuario> raiz = mock(Root.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final Predicate coincide = mock(Predicate.class);

    @Test
    @DisplayName("un uid completo se compara, exacto, con el public_id; sin distinguir mayusculas")
    @SuppressWarnings("unchecked")
    void uidCompleto() {
        UUID uid = UUID.fromString("6f1c2d3e-4a5b-4c6d-8e9f-0a1b2c3d4e5f");
        Path<Object> publicId = mock(Path.class);
        when(raiz.get("publicId")).thenReturn(publicId);
        when(cb.equal(publicId, uid)).thenReturn(coincide);

        assertThat(predicadoPara("6f1c2d3e-4a5b-4c6d-8e9f-0a1b2c3d4e5f")).isSameAs(coincide);
        assertThat(predicadoPara("6F1C2D3E-4A5B-4C6D-8E9F-0A1B2C3D4E5F")).isSameAs(coincide);
        verify(cb, org.mockito.Mockito.times(2)).equal(publicId, uid);
    }

    @Test
    @DisplayName("solo cifras (hasta 18) se comparan, exacto, con la clave id")
    @SuppressWarnings("unchecked")
    void claveInterna() {
        Path<Object> id = mock(Path.class);
        when(raiz.get("id")).thenReturn(id);
        when(cb.equal(id, 1234L)).thenReturn(coincide);
        when(cb.equal(id, 999_999_999_999_999_999L)).thenReturn(coincide);

        assertThat(predicadoPara("1234")).isSameAs(coincide);
        assertThat(predicadoPara("999999999999999999")).isSameAs(coincide);
    }

    @ParameterizedTest(name = "«{0}» no es un identificador")
    @NullAndEmptySource
    @ValueSource(strings = {
        "ana", "ana@ejemplo.org", "Pérez", "12a", "-5", "1.5", "1 234",
        // 19 cifras: no cabe garantizado en un long; se busca como texto.
        "1234567890123456789",
        // uid a medias, con llaves, sin guiones o con letras que no son hexadecimales.
        "6f1c2d3e-4a5b", "{6f1c2d3e-4a5b-4c6d-8e9f-0a1b2c3d4e5f}", "6f1c2d3e4a5b4c6d8e9f0a1b2c3d4e5f",
        "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz", "6f1c2d3e-4a5b-4c6d-8e9f-0a1b2c3d4e5f0"})
    void noEsIdentificador(String texto) {
        assertThat(BusquedaDelDirectorio.porIdentificador(texto)).isEmpty();
    }

    private Predicate predicadoPara(String texto) {
        Optional<Specification<Usuario>> especificacion = BusquedaDelDirectorio.porIdentificador(texto);
        assertThat(especificacion).as("«%s» es un identificador", texto).isPresent();
        return especificacion.get().toPredicate(raiz, null, cb);
    }
}
