package com.nexusbattles.ms_identidad.auth.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Cubre el callback de persistencia que asigna el identificador publico. Es
 * codigo que Hibernate invoca por su cuenta antes del primer INSERT, asi que
 * ninguna prueba de servicio lo ejercita: hay que llamarlo directamente.
 */
class UsuarioTest {

    @Test
    void alPersistirPorPrimeraVezSeLeAsignaUnIdentificadorPublico() {
        Usuario usuario = new Usuario();

        usuario.asignarIdentificadorPublico();

        assertNotNull(usuario.getPublicId(), "un usuario nuevo tiene que salir con identificador publico");
    }

    /**
     * Si ya trae uno, no se toca. De lo contrario, reasignar una entidad ya
     * cargada le cambiaria el identificador, y cualquier servicio que la
     * referenciara —una puja de subastas, por ejemplo— quedaria apuntando a un
     * usuario que ya no existe con ese UUID.
     */
    @Test
    void noSobreescribeUnIdentificadorQueYaExiste() {
        Usuario usuario = new Usuario();
        UUID original = UUID.fromString("11111111-2222-3333-4444-555555555555");
        usuario.setPublicId(original);

        usuario.asignarIdentificadorPublico();

        assertEquals(original, usuario.getPublicId());
    }

    @Test
    void dosUsuariosNuevosNoCompartenIdentificador() {
        Usuario uno = new Usuario();
        Usuario otro = new Usuario();

        uno.asignarIdentificadorPublico();
        otro.asignarIdentificadorPublico();

        assertNotEquals(uno.getPublicId(), otro.getPublicId());
    }
}
