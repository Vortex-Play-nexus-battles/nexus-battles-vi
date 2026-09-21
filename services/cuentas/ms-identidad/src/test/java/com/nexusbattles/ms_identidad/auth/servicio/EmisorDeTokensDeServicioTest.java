package com.nexusbattles.ms_identidad.auth.servicio;

import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Un token de servicio se verifica con la misma clave publica que un token de
 * usuario, pero no puede confundirse con uno: no lleva uid ni ver, y su rol
 * no es ninguno de los cuatro roles de usuario.
 */
@DisplayName("EmisorDeTokensDeServicio · credenciales de servicio firmadas con la clave del emisor (ADR-005)")
class EmisorDeTokensDeServicioTest {

    private final ClavesDeFirma claves = new ClavesDeFirma("");

    private Claims verificar(String token) {
        return Jwts.parser().verifyWith(claves.publica()).build().parseSignedClaims(token).getPayload();
    }

    @Test
    @DisplayName("el token identifica al servicio: sujeto y azp = client_id, rol SERVICIO, sin uid ni ver")
    void formaDelToken() {
        EmisorDeTokensDeServicio emisor = new EmisorDeTokensDeServicio(claves, "ms-identidad", 15);

        EmisorDeTokensDeServicio.TokenEmitido emitido = emisor.emitir("salas-partidas");
        Claims claims = verificar(emitido.valor());

        assertAll(
                () -> assertEquals("salas-partidas", claims.getSubject()),
                () -> assertEquals("salas-partidas", claims.get("azp", String.class)),
                () -> assertEquals("SERVICIO", claims.get("rol", String.class)),
                () -> assertEquals("ms-identidad", claims.getIssuer()),
                () -> assertNull(claims.get("uid")),
                () -> assertNull(claims.get("ver")),
                () -> assertEquals(15 * 60, emitido.expiraEnSegundos()),
                () -> assertEquals(claves.identificador(),
                        Jwts.parser().verifyWith(claves.publica()).build()
                                .parseSignedClaims(emitido.valor()).getHeader().get("kid"))
        );
    }

    @Test
    @DisplayName("lo verifica la misma clave publica que verifica los tokens de usuario (mismo JWKS)")
    void mismaClaveQueLosTokensDeUsuario() {
        JwtService usuarios = new JwtService(claves);
        ReflectionTestUtils.setField(usuarios, "horasExpiracion", 1);
        ReflectionTestUtils.setField(usuarios, "emisor", "ms-identidad");
        EmisorDeTokensDeServicio servicios = new EmisorDeTokensDeServicio(claves, "ms-identidad", 15);

        String deServicio = servicios.emitir("correo").valor();

        // JwtService.validarYObtenerClaims usa la misma clave publica.
        assertEquals("correo", usuarios.validarYObtenerClaims(deServicio).getSubject());
    }

    @Test
    @DisplayName("caduca a los minutos configurados")
    void caduca() {
        Instant hace20Min = Instant.now().minusSeconds(20 * 60);
        Clock relojAtrasado = Clock.fixed(hace20Min, ZoneOffset.UTC);
        EmisorDeTokensDeServicio emisor = new EmisorDeTokensDeServicio(claves, "ms-identidad", 15, relojAtrasado);

        String token = emisor.emitir("correo").valor();

        assertThrows(ExpiredJwtException.class, () -> verificar(token));
    }

    @Test
    @DisplayName("sin client_id o con vigencia invalida no emite")
    void validaciones() {
        EmisorDeTokensDeServicio emisor = new EmisorDeTokensDeServicio(claves, "ms-identidad", 15);

        assertThrows(IllegalArgumentException.class, () -> emisor.emitir(" "));
        assertThrows(IllegalArgumentException.class, () -> emisor.emitir(null));
        assertThrows(IllegalArgumentException.class,
                () -> new EmisorDeTokensDeServicio(claves, "ms-identidad", 0));
    }
}
