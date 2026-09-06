package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.UUID;

/**
 * Otra escritura se guardo entre la lectura de la sala y su escritura — HU-SAL-002.
 *
 * <p>No es un error de negocio y no sale por la API: es la senal con la que la
 * persistencia le dice al caso de uso «lo que leiste ya no es la sala». El caso
 * de uso vuelve a leer y a aplicar las reglas; si el otro ingreso ocupo el
 * ultimo cupo, {@link Sala#unirse(UUID)} lo rechaza con el motivo de siempre.
 *
 * <p>Va en el dominio y no en persistencia porque el puerto
 * {@link RepositorioDeSalas} la promete: cualquier adaptador —JPA o el doble en
 * memoria de las pruebas— la lanza igual.
 */
public class SalaModificadaConcurrentemente extends RuntimeException {

    private final UUID idSala;

    public SalaModificadaConcurrentemente(UUID idSala) {
        super("La sala " + idSala + " cambio entre la lectura y la escritura.");
        this.idSala = idSala;
    }

    public UUID idSala() {
        return idSala;
    }
}
