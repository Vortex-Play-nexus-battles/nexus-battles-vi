package com.nexusbattles.ms_identidad.auth.service;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El @PrePersist de Usuario solo alcanza a los usuarios nuevos. Este componente
 * cubre a los que ya existian antes de que el campo se anadiera, y estas
 * pruebas fijan las tres propiedades que lo hacen seguro de ejecutar en cada
 * arranque: rellena, no toca nada si no hace falta, y no tumba el servicio si
 * la base de datos falla.
 */
@ExtendWith(MockitoExtension.class)
class RellenoDeIdentificadorPublicoTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private RellenoDeIdentificadorPublico relleno;

    @Test
    void asignaIdentificadorALosUsuariosQueNoLoTienen() {
        Usuario uno = new Usuario();
        Usuario otro = new Usuario();
        when(usuarioRepository.findByPublicIdIsNull()).thenReturn(List.of(uno, otro));

        relleno.rellenarLosQueFaltan();

        assertNotNull(uno.getPublicId());
        assertNotNull(otro.getPublicId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Usuario>> guardados = ArgumentCaptor.forClass(List.class);
        verify(usuarioRepository).saveAll(guardados.capture());
        assertEquals(2, guardados.getValue().size());
    }

    @Test
    void aCadaUnoLeDaUnIdentificadorDistinto() {
        Usuario uno = new Usuario();
        Usuario otro = new Usuario();
        when(usuarioRepository.findByPublicIdIsNull()).thenReturn(List.of(uno, otro));

        relleno.rellenarLosQueFaltan();

        assertEquals(2, java.util.Set.of(uno.getPublicId(), otro.getPublicId()).size(),
                "dos usuarios no pueden acabar con el mismo identificador: la columna es unica");
    }

    /**
     * Idempotente: corre en cada arranque, asi que cuando ya no queda ninguno
     * por rellenar no debe escribir en la base de datos.
     */
    @Test
    void siNoFaltaNingunoNoEscribeEnLaBaseDeDatos() {
        when(usuarioRepository.findByPublicIdIsNull()).thenReturn(List.of());

        relleno.rellenarLosQueFaltan();

        verify(usuarioRepository, never()).saveAll(anyList());
    }

    /**
     * Un fallo aqui no puede tumbar el arranque del servicio de identidad. Sin
     * identificador, un usuario antiguo sigue pudiendo entrar y usar su cuenta;
     * lo unico que no podra es operar en los servicios que lo exigen, y eso es
     * preferible a dejar a todo el mundo sin login.
     */
    @Test
    void unFalloDeLaBaseDeDatosNoTumbaElArranque() {
        when(usuarioRepository.findByPublicIdIsNull())
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sin conexion"));

        assertDoesNotThrow(() -> relleno.rellenarLosQueFaltan());
    }

    @Test
    void unFalloAlGuardarTampocoPropagaLaExcepcion() {
        when(usuarioRepository.findByPublicIdIsNull()).thenReturn(List.of(new Usuario()));
        when(usuarioRepository.saveAll(anyList()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("colision de unique"));

        assertDoesNotThrow(() -> relleno.rellenarLosQueFaltan());
    }

    @Test
    void noAlteraUnIdentificadorQueYaVieneAsignado() {
        Usuario conIdentificador = new Usuario();
        UUID original = UUID.fromString("11111111-2222-3333-4444-555555555555");
        conIdentificador.setPublicId(original);
        // La consulta no deberia devolverlo, pero si lo hiciera no se pierde el suyo.
        when(usuarioRepository.findByPublicIdIsNull()).thenReturn(List.of(conIdentificador));

        relleno.rellenarLosQueFaltan();

        assertEquals(original, conIdentificador.getPublicId());
    }
}
