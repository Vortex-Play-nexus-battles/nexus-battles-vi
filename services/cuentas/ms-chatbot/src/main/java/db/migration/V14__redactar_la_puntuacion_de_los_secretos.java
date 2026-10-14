package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * ms-chatbot.yaml 1.11.0 (7.4.8 «no almacenamiento de información sensible»).
 *
 * <p>Hasta la 1.10.0 la redacción dejaba sin tapar la puntuación en que
 * terminaba una contraseña: «mi contraseña es Hunter2024!» se guardaba como
 * «mi contraseña es ***!». Ese símbolo es parte de la contraseña (la política
 * de claves exige uno y suele ir al final). Desde la 1.11.0 se tapa el valor
 * entero; esta migración vuelve a redactar lo que ya estaba guardado —con
 * {@link V10__redactar_datos_sensibles_guardados}, que hace exactamente eso con
 * la redacción de hoy—, también lo que V10 redactó con la regla anterior:
 * «***!» vuelve a ser un valor anunciado y queda «***».
 *
 * <p>Las mismas tablas y las mismas reglas que V10: mensajes del usuario,
 * comentarios de las calificaciones, asunto, mensaje y contexto de los
 * tickets, y el ejemplo de las brechas (fundiendo las que queden iguales y
 * borrando las que solo eran el secreto). Una fila cuyo texto no cambia no se
 * toca: en una base nueva, donde V10 ya redactó con la regla corregida, no
 * hace nada.
 */
public class V14__redactar_la_puntuacion_de_los_secretos extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        new V10__redactar_datos_sensibles_guardados().migrate(context);
    }
}
