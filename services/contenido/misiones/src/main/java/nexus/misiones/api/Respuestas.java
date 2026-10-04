package nexus.misiones.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.Categoria;
import nexus.misiones.dominio.Dificultad;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.EstadoMision;
import nexus.misiones.dominio.Origen;

/**
 * Lo que devuelve el servicio, esquema por esquema de misiones.yaml. Son vistas
 * para el cliente, no el dominio: el dominio no se serializa tal cual (lo que
 * la semilla guarda de un enemigo, por ejemplo, no se publica).
 */
final class Respuestas {

    private Respuestas() {
    }

    record Pagina(List<Resumen> misiones, int total, int pagina, int totalPaginas, int tamanio) {
    }

    record Resumen(String id, String nombre, Categoria categoria, String descripcionBreve, String imagen,
                   Dificultad dificultad, double duracionHoras, Integer nivelRecomendado,
                   List<String> recompensasDestacadas, EstadoMision estado, String motivoBloqueo, Double progreso,
                   String ultimaEjecucionId, boolean destacada, boolean nueva, Instant disponibleHasta,
                   boolean favorita, Origen origen) {
    }

    /** {@code Mision}: el resumen, aplanado, y el detalle. */
    record Detalle(@JsonUnwrapped Resumen resumen, String narrativa, String escenario, Objetivos objetivos,
                   List<String> requisitosPrevios, Integer encuentros, Escalon escalon,
                   List<EscalonVista> escalones, List<Enemigo> enemigos, JefeVista jefe, Double probabilidadMaster,
                   List<MasterVista> masters, List<MasterVista> mastersPorTipoDeHeroe, RecompensasVista recompensas,
                   ExperienciaVista experiencia, IntentosVista intentos) {
    }

    record Objetivos(List<String> principales, List<String> secundarios) {
    }

    record EscalonVista(Escalon id, boolean desbloqueado, Double multiplicadorDeEstadisticas) {
    }

    record Enemigo(String nombre, Integer cantidad, String descripcion) {
    }

    record JefeVista(String nombre, String prototipo, Integer vida, String descripcion) {
    }

    record MasterVista(String nombre, String prototipo, Double probabilidad, EpicaVista epica) {
    }

    record EpicaVista(String nombre, String efectoGeneral, String efectoPotenciado) {
    }

    record RecompensasVista(List<String> garantizadas, List<Potencial> potenciales, List<PorObjetivo> porObjetivos,
                            List<String> primeraVez) {
    }

    record Potencial(String nombre, double probabilidad, String detalle) {
    }

    record PorObjetivo(String objetivo, String recompensa) {
    }

    record ExperienciaVista(String porEnemigo, double porCompletar) {
    }

    record IntentosVista(int maximo, String periodo, int restantes) {
    }

    record MisionActiva(UUID ejecucionId, String misionId, String nombre, Categoria categoria, HeroeVista heroe,
                        Instant iniciadaEn, Instant terminaEn, Double progreso, String penalizacion, Escalon escalon,
                        String estado) {
    }

    record HeroeVista(String id, String nombre, String prototipo, Integer nivel) {
    }

    record Cancelacion(UUID ejecucionId, String estado, boolean heroeLiberado, String penalizacion) {
    }

    record Reporte(UUID ejecucionId, MisionCorta mision, String resultado, Escalon escalon, long duracionMs,
                   Instant terminadaEn, HeroeDelReporte heroe, Combate combate,
                   List<EnemigoDerrotado> enemigosDerrotados, List<MasterDerrotado> mastersDerrotados,
                   boolean jefeDerrotado, RecompensasDelReporte recompensas, List<ObjetivoVista> objetivos) {
    }

    record MisionCorta(String id, String nombre, Categoria categoria) {
    }

    record HeroeDelReporte(String id, String nombre, String prototipo, Integer nivel, Integer nivelAlcanzado) {
    }

    record Combate(int encuentros, int danoInfligido, int danoRecibido, int turnos,
                   List<HabilidadUsada> habilidadesMasUsadas, int criticos) {
    }

    record HabilidadUsada(String nombre, int usos) {
    }

    record EnemigoDerrotado(String nombre, int cantidad) {
    }

    record MasterDerrotado(String nombre, String epica) {
    }

    record RecompensasDelReporte(int creditos, List<Producto> productos, List<String> epicas, double experiencia,
                                 List<SinEntregar> sinEntregar, boolean entregaPendiente) {
    }

    record Producto(String nombre, String rareza) {
    }

    record SinEntregar(String nombre, String motivo) {
    }

    record ObjetivoVista(String texto, boolean cumplido, String bonificacion) {
    }

    record Historial(List<Completada> completadas, List<PorCategoria> porCategoria, List<MejorTiempo> mejoresTiempos,
                     List<EpicaObtenida> epicas, List<Cadena> cadenas) {
    }

    record Completada(UUID ejecucionId, String misionId, String nombre, Categoria categoria, Instant terminadaEn,
                      String resultado, long duracionMs) {
    }

    record PorCategoria(Categoria categoria, int completadas, int fallidas) {
    }

    record MejorTiempo(String misionId, String nombre, long duracionMs) {
    }

    record EpicaObtenida(String nombre, String master, Instant obtenidaEn) {
    }

    record Cadena(String nombre, int completadas, int total) {
    }

    record Estrategia(String heroeId, String prototipo, int nivel, List<RotacionVista> rotaciones,
                      Instant actualizadaEn) {
    }

    record RotacionVista(String prioridad, List<String> pasos) {
    }
}
