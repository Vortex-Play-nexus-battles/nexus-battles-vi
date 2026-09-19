package com.nexusbattles.ms_finanzas.partidas.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nexusbattles.ms_finanzas.partidas.MisCofresConsultaService;
import com.nexusbattles.ms_finanzas.partidas.ResumenCofre;

@ExtendWith(MockitoExtension.class)
class MisCofresControllerTest {

    @Mock
    private MisCofresConsultaService consultaService;

    @InjectMocks
    private MisCofresController controller;

    private Principal principal(String uid) {
        return () -> uid;
    }

    @Test
    void mios_usaUidDelPrincipalYDevuelveLista() {
        String uid = UUID.randomUUID().toString();
        ResumenCofre cofre = new ResumenCofre(UUID.randomUUID(),
                "COFRE_ESTANDAR_v1", Instant.parse("2026-09-16T10:00:00Z"));
        Pageable esperado = PageRequest.of(0, 20);
        Page<ResumenCofre> pagina = new PageImpl<>(List.of(cofre), esperado, 1);
        when(consultaService.listarPorUsuario(eq(uid), eq(esperado))).thenReturn(pagina);

        ResponseEntity<Page<ResumenCofre>> resp = controller.mios(principal(uid), 0, 20);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getContent()).hasSize(1);
    }

    @Test
    void mios_tamanoPaginaMayorAlTope_seCapa() {
        String uid = UUID.randomUUID().toString();
        Pageable esperado = PageRequest.of(0, 100);
        when(consultaService.listarPorUsuario(eq(uid), eq(esperado))).thenReturn(Page.empty(esperado));

        controller.mios(principal(uid), 0, 999_999);
    }

    @Test
    void mios_paginaNegativaYTamanoCero_seNormalizan() {
        String uid = UUID.randomUUID().toString();
        Pageable esperado = PageRequest.of(0, 1);
        when(consultaService.listarPorUsuario(eq(uid), eq(esperado))).thenReturn(Page.empty(esperado));

        controller.mios(principal(uid), -1, 0);
    }
}
