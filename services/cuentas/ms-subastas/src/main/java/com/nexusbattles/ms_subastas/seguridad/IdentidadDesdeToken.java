package com.nexusbattles.ms_subastas.seguridad;

import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto {@link IdentidadClient} que define HU-SUB-001 (Edwin),
 * resolviendo la identidad desde el JWT de la peticion en curso.
 *
 * <p>Hasta ahora el puerto no tenia implementacion: sus pruebas lo sustituyen
 * por un doble, asi que en produccion {@code identidad.actual()} no habria
 * encontrado bean. Esto lo completa leyendo el encabezado
 * {@code Authorization} y validandolo con {@link ValidadorDeToken}.
 *
 * <p><b>El UUID se exige, no se supone.</b> Todas las operaciones que llegan
 * por este puerto mueven creditos — publicar cobra comision de publicacion,
 * pujar y comprar retienen saldo — asi que un token sin el claim {@code uid}
 * falla aqui con {@link TokenInvalidoException} en vez de dejar pasar una
 * peticion cuyo dueno no se puede determinar. Crear una subasta con vendedor
 * nulo, o retener creditos a nombre de nadie, es peor que negar el acceso.
 *
 * <p><b>Limitacion conocida:</b> {@code esMaestroDeJuego} devuelve siempre
 * {@code false}, porque el token no trae ese dato y ms-identidad no tiene ese
 * rol — sus roles son Jugador, Moderador, Administrador, Super Administrador y
 * cuentas institucionales. {@code false} es el valor seguro: el Maestro de
 * Juego esta exento de la comision de publicacion, asi que devolverlo en
 * {@code true} por defecto regalaria exenciones a cualquiera. Queda pendiente
 * acordar con Edwin y con el dueno de identidad de donde sale ese dato: si es
 * un rol nuevo en ms-identidad que viaje en el claim {@code rol}, o un atributo
 * propio de subastas.
 */
@Component
public class IdentidadDesdeToken implements IdentidadClient {

    private static final String ENCABEZADO = "Authorization";

    private final ValidadorDeToken validador;
    private final HttpServletRequest peticion;

    /**
     * @param peticion Spring inyecta aqui un proxy con alcance de peticion, asi
     *                 que este componente puede ser un singleton y aun asi ver
     *                 la peticion que se esta atendiendo en cada hilo.
     */
    public IdentidadDesdeToken(ValidadorDeToken validador, HttpServletRequest peticion) {
        this.validador = validador;
        this.peticion = peticion;
    }

    @Override
    public Identidad actual() {
        IdentidadDelSolicitante solicitante = validador.validarEncabezado(peticion.getHeader(ENCABEZADO));
        return new Identidad(solicitante.exigirJugadorId(), false);
    }
}
