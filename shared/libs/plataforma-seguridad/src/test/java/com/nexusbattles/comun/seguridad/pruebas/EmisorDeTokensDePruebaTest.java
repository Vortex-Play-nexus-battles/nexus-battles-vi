package com.nexusbattles.comun.seguridad.pruebas;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import com.nexusbattles.comun.seguridad.servicio.ActorDeServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El emisor de prueba tiene que ser indistinguible de ms-identidad para la
 * cadena de seguridad: si aqui pasara algo que alla no pasa, las pruebas de
 * los servicios estarian dando verde sobre un token que en produccion daria
 * 401 (o al reves, que es peor).
 */
@DisplayName("EmisorDeTokensDePrueba · tokens reales verificados por un decodificador real")
class EmisorDeTokensDePruebaTest {

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private final JwtDecoder decodificador = emisor.decodificador();
    private final ConversorRolesJwt conversor = new ConversorRolesJwt();

    @Test
    @DisplayName("un token de jugador se verifica contra el JWKS y sale con uid, apodo y ROLE_JUGADOR")
    void tokenDeJugador() {
        UUID uid = UUID.randomUUID();

        Jwt jwt = decodificador.decode(emisor.tokenDeJugador("lyra_roja", uid));
        AbstractAuthenticationToken autenticacion = conversor.convert(jwt);

        assertThat(jwt.getHeaders()).containsEntry("kid", emisor.kid());
        // Como ms-identidad, el emisor no es una URL: se lee como texto.
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(EmisorDeTokensDePrueba.EMISOR);
        assertThat(IdentidadDelToken.idDe(jwt)).isEqualTo(uid);
        assertThat(IdentidadDelToken.apodoDe(jwt)).isEqualTo("lyra_roja");
        assertThat(autenticacion.getName()).isEqualTo(uid.toString());
        // Spring Security 7 anade FACTOR_BEARER como autoridad del factor de
        // autenticacion; el rol de negocio es lo que se afirma.
        assertThat(roles(autenticacion)).containsExactly("ROLE_JUGADOR");
        assertThat(IdentidadDelToken.idDe(autenticacion)).isEqualTo(uid);
        assertThat(IdentidadDelToken.apodoDe(autenticacion)).isEqualTo("lyra_roja");
    }

    @Test
    @DisplayName("un token de servicio no lleva usuario: azp = client_id y ROLE_SERVICIO")
    void tokenDeServicio() {
        Jwt jwt = decodificador.decode(emisor.tokenDeServicio("salas-partidas"));
        AbstractAuthenticationToken autenticacion = conversor.convert(jwt);

        assertThat(ActorDeServicio.desde(jwt)).contains("salas-partidas");
        assertThat(roles(autenticacion)).containsExactly("ROLE_SERVICIO");
        assertThat(jwt.hasClaim("uid")).isFalse();
        assertThatThrownBy(() -> IdentidadDelToken.idDe(jwt))
                .as("un token de servicio nunca identifica a un jugador")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la forma de Keycloak tambien pasa: roles de realm_access y sujeto estable")
    void tokenDeKeycloak() {
        UUID sujeto = UUID.randomUUID();

        Jwt jwt = decodificador.decode(emisor.tokenDeKeycloak(sujeto, "ana", List.of("JUGADOR", "MODERADOR")));
        AbstractAuthenticationToken autenticacion = conversor.convert(jwt);

        assertThat(IdentidadDelToken.idDe(jwt)).isEqualTo(sujeto);
        assertThat(IdentidadDelToken.apodoDe(jwt)).isEqualTo("ana");
        assertThat(roles(autenticacion)).containsExactlyInAnyOrder("ROLE_JUGADOR", "ROLE_MODERADOR");
    }

    private static List<String> roles(AbstractAuthenticationToken autenticacion) {
        return autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autoridad -> autoridad.startsWith("ROLE_"))
                .toList();
    }

    @Test
    @DisplayName("un token caducado no pasa el decodificador")
    void tokenCaducado() {
        assertThatThrownBy(() -> decodificador.decode(emisor.tokenCaducado("lyra", UUID.randomUUID())))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("un token firmado con una clave que no esta en el JWKS no pasa: es un token fabricado")
    void tokenFirmadoPorOtro() {
        assertThatThrownBy(() -> decodificador.decode(emisor.tokenFirmadoPorOtro("lyra", UUID.randomUUID())))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("un token manipulado despues de firmado no pasa")
    void tokenManipulado() {
        String legitimo = emisor.tokenDeJugador("lyra", UUID.randomUUID());
        String[] partes = legitimo.split("\\.");
        String cuerpoAjeno = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"admin\",\"rol\":\"SUPER_ADMINISTRADOR\",\"exp\":4102444800}".getBytes());
        String manipulado = partes[0] + "." + cuerpoAjeno + "." + partes[2];

        assertThatThrownBy(() -> decodificador.decode(manipulado)).isInstanceOf(JwtException.class);
    }
}
