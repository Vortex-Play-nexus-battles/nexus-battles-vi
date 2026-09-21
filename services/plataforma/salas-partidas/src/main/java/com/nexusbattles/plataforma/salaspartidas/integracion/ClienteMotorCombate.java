package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.MotorNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResolucionDelMotor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador hacia el motor de combate — espejo de
 * {@code contracts/openapi/motor-combate.yaml}.
 *
 * <p>Habla con {@code POST /api/v1/combate/ataques}. El motor no guarda nada
 * entre llamadas: cada peticion se resuelve entera, asi que este cliente no
 * tiene que mantener sesion ni identificador de partida.
 *
 * <p><b>Que manda y que no.</b> Manda el nombre del heroe atacante —el motor
 * pide sus estadisticas al catalogo por su cuenta— y la vida del objetivo como
 * defensa. Este servicio no conoce la defensa real de un heroe: no la guarda ni
 * la debe guardar. Ver la nota de abajo.
 *
 * <p><b>Cualquier fallo es {@link MotorNoDisponible}</b>, incluido un cuerpo que
 * no se entiende. No se traduce a un dano de cero: un cero se confunde con un
 * ataque fallido y decidiria el combate con un numero que nadie calculo.
 */
public class ClienteMotorCombate implements MotorDeCombate {

    /**
     * Prototipo de distribucion con el que se pide la resolucion.
     *
     * <p><b>Decision funcional pendiente, documentada y no inventada.</b> El
     * motor define seis prototipos y el catalogo de heroes tiene mas tipos; en
     * ningun sitio del repositorio existe el mapeo heroe → prototipo, y el
     * Product Owner no lo ha fijado. Se usa uno equilibrado para todos y queda
     * anotado en el contrato del motor: el dia que exista el mapeo, se cambia
     * aqui una linea.
     */
    static final String PROTOTIPO_POR_DEFECTO = "GUERRERO_ARMAS";

    private final RestClient http;
    private final String base;

    public ClienteMotorCombate(RestClient http, String base) {
        this.http = http;
        this.base = base;
    }

    @Override
    public ResolucionDelMotor resolver(HeroeDeCombate atacante, HeroeDeCombate objetivo) {
        try {
            Respuesta respuesta = http.post()
                    .uri(base + "/api/v1/combate/ataques")
                    .body(new Peticion(nombreParaElMotor(atacante), defensaDe(objetivo),
                            new Peticion.Distribucion(PROTOTIPO_POR_DEFECTO)))
                    .retrieve()
                    .body(Respuesta.class);

            if (respuesta == null || respuesta.categoria() == null) {
                throw new MotorNoDisponible("el motor respondio un cuerpo que no se entiende");
            }
            return new ResolucionDelMotor(respuesta.categoria(),
                    respuesta.danoAplicado(), respuesta.ataqueResuelto());

        } catch (RestClientException noResponde) {
            throw new MotorNoDisponible(noResponde.getMessage());
        }
    }

    /**
     * Defensa del objetivo.
     *
     * <p><b>La simplificacion que habia aqui hacia el combate imposible.</b> Se
     * mandaba la vida actual del objetivo como defensa, anotado como
     * aproximacion consciente. Pero el motor acierta si
     * {@code ataqueResuelto > defensa}, y los dos numeros no estan en la misma
     * escala ni de lejos: «Guerrero Tanque» ataca con {@code 10+1d6} —de 11 a
     * 16— y tiene 44 de vida. Ningun golpe podia acertar <b>nunca</b>, con
     * ningun prototipo, porque la vida de todos esta muy por encima de
     * cualquier tirada. No era una aproximacion: era un tope insuperable, y el
     * combate entero no funcionaba. Lo destapo el E2E del corte vertical, que
     * recibia {@code SIN_EFECTO} en cada golpe.
     *
     * <p>Ahora se manda la defensa de verdad, la del prototipo, que
     * {@code ClienteInventarioHeroes} resuelve contra el catalogo de heroes al
     * construir la ficha. Cuando no se conoce —fichas anteriores a V8, o el
     * catalogo sin contestar— se cae a la vida, que es lo que habia: se degrada
     * al comportamiento anterior en vez de mandar un numero inventado.
     */
    private static int defensaDe(HeroeDeCombate objetivo) {
        Integer defensa = objetivo.defensa();
        return defensa == null ? objetivo.vidaActual() : defensa;
    }

    /**
     * Lo que el motor busca en el catalogo: el <b>prototipo</b>, no el nombre.
     *
     * <p>El motor resuelve al atacante con {@code GET /api/v1/heroes/{nombre}},
     * y ese catalogo indexa por prototipo —«Guerrero Tanque»—. Aqui se mandaba
     * {@code atacante.nombre()}, que es el nombre propio que le puso su dueno
     * —«Aquiles»—: el catalogo devolvia 404 y <b>ningun ataque se resolvia</b>.
     * El fallo era invisible porque el error viajaba a la cola privada del
     * jugador, que la vista no escuchaba. Lo destapo el E2E del corte vertical.
     *
     * <p>El prototipo lo resuelve {@code ClienteInventarioHeroes} contra
     * productos al construir la ficha. Cuando no se conoce —fichas anteriores a
     * V8, o productos sin contestar— se manda el nombre, que es lo que habia:
     * funciona si el heroe se llama como su prototipo y falla igual que antes si
     * no. Es peor callar el caso que degradar al comportamiento anterior.
     */
    private static String nombreParaElMotor(HeroeDeCombate atacante) {
        String prototipo = atacante.prototipo();
        return prototipo == null || prototipo.isBlank() ? atacante.nombre() : prototipo;
    }

    /** Espejo de {@code PeticionDeAtaque} del contrato del motor. */
    private record Peticion(String heroeAtacante, int defensaObjetivo, Distribucion distribucion) {

        private record Distribucion(String prototipo) {
        }
    }

    /** Espejo de {@code ResolucionDeAtaque}. Se ignora lo que no se usa. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record Respuesta(String categoria, int danoAplicado, int ataqueResuelto) {
    }
}
