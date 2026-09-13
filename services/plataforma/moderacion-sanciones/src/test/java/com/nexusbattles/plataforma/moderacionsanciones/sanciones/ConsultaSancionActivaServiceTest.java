package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultaSancionActivaServiceTest {

    private final ConsultaSancionActivaService service = new ConsultaSancionActivaService();

    @Test
    void respondeSinSancionParaCualquierUsuario() {
        // Backing provisional de Sprint 2 (acordado con ms-subastas, HU-SUB-001):
        // todavia no existe el modelo de sanciones, asi que nadie puede estar
        // sancionado. Esto cambia en Sprint 3 sin tocar la firma del metodo.
        var resultado = service.consultar(UUID.randomUUID());

        assertThat(resultado.sancionActiva()).isFalse();
        assertThat(resultado.motivo()).isNull();
        assertThat(resultado.vigenteHasta()).isNull();
    }
}
