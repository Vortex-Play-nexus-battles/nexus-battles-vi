package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.EntradaListaDeseosDto;
import com.nexusbattles.ms_ecommerce.seguridad.ConversorDeRoles;
import com.nexusbattles.ms_ecommerce.service.ListaDeDeseosService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * La lista de deseos del jugador (contrato 1.3.0, 7.5), de su {@code uid}.
 *
 * <p>{@code PUT} y {@code DELETE} sobre el producto, y no un {@code POST} que
 * cree entradas: la lista es un conjunto, anadir dos veces o quitar lo que no
 * estaba no cambia nada, y el navegador puede repetir sin miedo.
 */
@RestController
@RequestMapping("/api/v1/lista-deseos")
@RequiredArgsConstructor
public class ListaDeDeseosController {

    private final ListaDeDeseosService deseos;

    @GetMapping
    public ResponseEntity<List<EntradaListaDeseosDto>> miLista(@AuthenticationPrincipal Jwt usuario) {
        return ResponseEntity.ok(deseos.de(ConversorDeRoles.identificadorDe(usuario)));
    }

    @PutMapping("/{productoId}")
    public ResponseEntity<EntradaListaDeseosDto> desear(@AuthenticationPrincipal Jwt usuario,
                                                        @PathVariable String productoId) {
        return ResponseEntity.ok(deseos.anadir(ConversorDeRoles.identificadorDe(usuario), productoId));
    }

    @DeleteMapping("/{productoId}")
    public ResponseEntity<Void> dejarDeDesear(@AuthenticationPrincipal Jwt usuario, @PathVariable String productoId) {
        deseos.quitar(ConversorDeRoles.identificadorDe(usuario), productoId);
        return ResponseEntity.noContent().build();
    }
}
