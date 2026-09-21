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
 * <p><b>{@code esMaestroDeJuego} devuelve siempre {@code false}, y es una
 * decision acordada, no un pendiente olvidado.</b> Ese rol no existe
 * formalmente en ms-identidad — sus roles son Jugador, Moderador,
 * Administrador, Super Administrador y cuentas institucionales— y no se
 * inventa desde subastas. {@code false} es ademas el valor seguro: el Maestro
 * de Juego esta exento de la comision de publicacion, asi que suponerlo
 * {@code true} regalaria exenciones a cualquiera. Consecuencia buscada: hasta
 * nuevo aviso, <b>todos pagan comision</b>.
 *
 * <p>Lo resuelve <b>HU-SUB-010 del Sprint 3</b>, que tiene como pendiente
 * explicito definir ese rol o cuenta especial. Cuando se defina, ms-identidad
 * sigue siendo la fuente de verdad y decidira si viaja como un rol nuevo en el
 * claim {@code rol} o como un atributo propio. Acordado con Edwin (HU-SUB-001)
 * el 14/09/2026; no cambiar esto antes de esa definicion.
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
