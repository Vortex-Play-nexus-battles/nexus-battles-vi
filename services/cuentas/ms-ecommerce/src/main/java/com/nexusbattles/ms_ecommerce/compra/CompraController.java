package com.nexusbattles.ms_ecommerce.compra;

import com.nexusbattles.ms_ecommerce.compra.pago.SolicitudDePago;
import com.nexusbattles.ms_ecommerce.seguridad.ConversorDeRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * La compra y las ordenes del jugador (contrato 1.3.0/1.4.0): {@code POST
 * /checkout}, {@code GET /ordenes} y {@code GET /ordenes/{ordenId}}.
 *
 * <p>El jugador sale del {@code uid} del token, nunca del cuerpo. La
 * {@code Idempotency-Key} se lee como opcional para que su ausencia responda
 * con el {@code type} propio del contrato ({@code clave-de-idempotencia-requerida})
 * y no con el 400 generico de Spring.
 */
@RestController
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compras;

    @PostMapping("/api/v1/checkout")
    public ResponseEntity<OrdenDto> pagar(@AuthenticationPrincipal Jwt usuario,
                                         @RequestHeader(name = "Idempotency-Key", required = false) String clave,
                                         @RequestBody SolicitudDePago solicitud) {
        ResultadoDeCompra resultado = compras.pagar(ConversorDeRoles.identificadorDe(usuario), clave, solicitud);
        return ResponseEntity.status(resultado.creada() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.orden());
    }

    @GetMapping("/api/v1/ordenes")
    public ResponseEntity<List<OrdenDto>> misOrdenes(@AuthenticationPrincipal Jwt usuario) {
        return ResponseEntity.ok(compras.ordenesDe(ConversorDeRoles.identificadorDe(usuario)));
    }

    @GetMapping("/api/v1/ordenes/{ordenId}")
    public ResponseEntity<OrdenDto> miOrden(@AuthenticationPrincipal Jwt usuario, @PathVariable String ordenId) {
        return ResponseEntity.ok(compras.ordenDe(ConversorDeRoles.identificadorDe(usuario), ordenId));
    }
}
