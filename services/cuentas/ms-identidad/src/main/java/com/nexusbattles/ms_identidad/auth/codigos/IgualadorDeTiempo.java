package com.nexusbattles.ms_identidad.auth.codigos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * El mismo trabajo caro en el camino que no tiene nada que hacer.
 *
 * <p>Las rutas publicas de B1 responden lo mismo exista o no la cuenta
 * («Si existe una cuenta asociada, recibiras instrucciones.»). Pero la
 * respuesta no es lo unico que se ve: el tiempo tambien. Si con una cuenta
 * real se calcula un BCrypt (~70 ms) y sin ella se responde en 2 ms,
 * cronometrar la peticion dice que correos estan registrados aunque el
 * cuerpo no lo diga. Aqui se paga ese mismo BCrypt contra un resumen de
 * relleno, para que los dos caminos cuesten lo mismo.
 *
 * <p>Mismo cifrador y mismo coste que el camino real (BCrypt, 10 rondas):
 * con otro coste el tiempo volveria a delatar la diferencia.
 */
@Component
public class IgualadorDeTiempo {

    private final PasswordEncoder resumidor;
    /**
     * Valor al azar de cada arranque: el relleno no es ningun secreto, pero
     * tampoco un literal del codigo que alguien pudiera tomar por una clave.
     */
    private final String valorDeRelleno;
    private final String resumenDeRelleno;

    @Autowired
    public IgualadorDeTiempo() {
        this(new BCryptPasswordEncoder());
    }

    public IgualadorDeTiempo(PasswordEncoder resumidor) {
        this.resumidor = resumidor;
        byte[] azar = new byte[16];
        new SecureRandom().nextBytes(azar);
        this.valorDeRelleno = HexFormat.of().formatHex(azar);
        this.resumenDeRelleno = resumidor.encode(valorDeRelleno);
    }

    /** Lo que cuesta comprobar un codigo o una contrasena que existiera. */
    public void comparar(String entrada) {
        resumidor.matches(GeneradorDeCodigos.normalizar(entrada), resumenDeRelleno);
    }

    /** Lo que cuesta emitir un codigo (resumirlo) cuando no se emite ninguno. */
    public void resumir() {
        resumidor.encode(valorDeRelleno);
    }
}
