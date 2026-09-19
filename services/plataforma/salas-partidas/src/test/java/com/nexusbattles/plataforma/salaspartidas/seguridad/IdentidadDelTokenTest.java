package com.nexusbattles.plataforma.salaspartidas.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Las dos caras de la identidad — ADR-002.
 *
 * <p>Esta lectura estaba escrita dos veces y una de las dos tenia un fallo que
 * devolvia 500: {@code UUID.fromString(sub)} sobre un token de
 * {@code ms-identidad}, cuyo sujeto es el <b>apodo</b>. Se corrigio en
 * {@code SalasController} (PR #404) y se quedo mal en {@code ChatController}.
 * Estas pruebas fijan la regla para que no vuelva a divergir.
 */
@DisplayName("IdentidadDelToken · el id sale de uid y el apodo del nombre visible")
class IdentidadDelTokenTest {

    private static final UUID UID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static Jwt token(String sujeto, Map<String, Object> claims) {
        Jwt.Builder constructor = Jwt.withTokenValue("da-igual")
                .header("alg", "none")
                .subject(sujeto)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(constructor::claim);
        return constructor.build();
    }

    @Test
    @DisplayName("con un token de ms-identidad el id sale de uid, no del sujeto")
    void elIdSaleDeUid() {
        // Forma real tras ADR-002: el sujeto es el apodo. Leerlo como UUID es
        // exactamente lo que reventaba.
        Jwt jwt = token("demo_grupo6", Map.of("uid", UID.toString()));

        assertEquals(UID, IdentidadDelToken.idDe(jwt));
    }

    @Test
    @DisplayName("sin uid se cae al sujeto: los tokens anteriores a ADR-002 siguen valiendo")
    void sinUidValeElSujeto() {
        assertEquals(UID, IdentidadDelToken.idDe(token(UID.toString(), Map.of())));
    }

    @Test
    @DisplayName("un uid en blanco no cuenta como uid")
    void uidEnBlancoNoCuenta() {
        assertEquals(UID, IdentidadDelToken.idDe(token(UID.toString(), Map.of("uid", "   "))));
    }

    @Test
    @DisplayName("un token sin ningun identificador utilizable falla a la vista, no en silencio")
    void sinIdentificadorFalla() {
        // Taparlo daria un 200 con la identidad equivocada, que es peor que el
        // error.
        assertThrows(IllegalArgumentException.class,
                () -> IdentidadDelToken.idDe(token("demo_grupo6", Map.of())));
    }

    @Test
    @DisplayName("el apodo sale de preferred_username, que es lo que reconoce el inventario")
    void elApodoSaleDelNombreVisible() {
        Jwt jwt = token("demo_grupo6",
                Map.of("uid", UID.toString(), "preferred_username", "Simon_P"));

        assertEquals("Simon_P", IdentidadDelToken.apodoDe(jwt));
    }

    @Test
    @DisplayName("sin preferred_username vale apodo, y en ultimo caso el sujeto")
    void cadenaDeRespaldoDelApodo() {
        assertEquals("Ana", IdentidadDelToken.apodoDe(
                token("da-igual", Map.of("uid", UID.toString(), "apodo", "Ana"))));
        assertEquals("demo_grupo6", IdentidadDelToken.apodoDe(
                token("demo_grupo6", Map.of("uid", UID.toString()))));
    }
}
