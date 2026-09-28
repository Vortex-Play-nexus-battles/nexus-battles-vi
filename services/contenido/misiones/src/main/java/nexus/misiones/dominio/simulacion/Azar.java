package nexus.misiones.dominio.simulacion;

/**
 * La fuente de azar de una ejecucion: quien empieza cada duelo, el 1d8 de la
 * experiencia, la aparicion de un Master y el botin.
 *
 * <p>Una sola fuente por ejecucion, creada con la semilla que se guardo al
 * matricular: asi las pruebas son reproducibles (7.8.12, «generador de
 * numeros pseudo-aleatorios»). Lo que decide si un golpe acierta y cuanto dano
 * hace NO sale de aqui: es del motor de combate y su tabla de 8.000 filas.
 */
public interface Azar {

    /** Un entero uniforme entre los dos extremos, ambos incluidos (un dado). */
    int entre(int minimo, int maximo);

    /** Verdadero con la probabilidad dada (0 a 1). */
    boolean acierta(double probabilidad);

    /** Un entero largo cualquiera, para derivar semillas. */
    long largo();
}
