package nexus.misiones.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un turno de combate en la coleccion {@code eventos_de_combate} de la base de
 * misiones (HU-SIM-003). Es la materia prima de «datos almacenados para IA»
 * (HU-SIM-008), por eso se guarda tal cual lo produjo la simulacion, sin
 * resumir.
 *
 * <p>El {@code _id} es {@code <ejecucion>:<secuencia>}: determinista, asi que
 * volver a escribir el mismo turno no puede duplicarlo. Los indices:
 * <ul>
 *   <li>{@code ejecucion_secuencia}: unico, es como se lee («los turnos de esta
 *       ejecucion, en orden») y garantiza que una ejecucion no tenga dos turnos
 *       con la misma secuencia;</li>
 *   <li>{@code mision}: para los analisis por mision que hara el modelo de IA.</li>
 * </ul>
 *
 * <p>Los campos del evento van como el record del dominio, sin renombrar: es el
 * formato estable que lee quien consuma estos datos.
 */
@Document(collection = "eventos_de_combate")
@CompoundIndexes({
        @CompoundIndex(name = "ejecucion_secuencia", def = "{'ejecucionId': 1, 'secuencia': 1}", unique = true),
        @CompoundIndex(name = "mision", def = "{'misionId': 1}")
})
record EventoDeCombateDocumento(
        @Id String id,
        String ejecucionId,
        String misionId,
        int secuencia,
        int encuentro,
        String enemigo,
        int turno,
        EventoDeCombate.Actor actor,
        EventoDeCombate.Estados antes,
        List<nexus.misiones.dominio.simulacion.Suceso> alIniciar,
        EventoDeCombate.Jugada jugada,
        EventoDeCombate.Estados despues,
        Instant registradoEn) {

    static EventoDeCombateDocumento de(EventoDeCombate e, Instant ahora) {
        return new EventoDeCombateDocumento(e.ejecucionId() + ":" + e.secuencia(), e.ejecucionId().toString(),
                e.misionId(), e.secuencia(), e.encuentro(), e.enemigo(), e.turno(), e.actor(), e.antes(),
                e.alIniciar(), e.jugada(), e.despues(), ahora);
    }

    EventoDeCombate aDominio() {
        return new EventoDeCombate(UUID.fromString(ejecucionId), misionId, secuencia, encuentro, enemigo, turno,
                actor, antes, alIniciar, jugada, despues);
    }
}
