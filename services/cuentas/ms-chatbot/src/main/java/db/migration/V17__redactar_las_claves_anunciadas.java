package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * ms-chatbot.yaml 1.12.0 (7.4.8 «no almacenamiento de información sensible»).
 *
 * <p>Hasta la 1.11.0 una palabra ambigua («clave», «token», «secret») seguida
 * de «es» nunca anunciaba un secreto, así que «mi clave es Hunter2024!» se
 * guardaba tal cual (segunda auditoría del 7.4). Desde la 1.12.0 se tapa
 * cuando el valor parece una contraseña (cifras, símbolos o mayúsculas por
 * dentro); «la clave es practicar» sigue igual.
 *
 * <p>Esta migración vuelve a redactar lo guardado con
 * {@link V10__redactar_datos_sensibles_guardados}, que aplica la redacción de
 * hoy a las mismas tablas que V10 y V14: mensajes del usuario, comentarios de
 * las calificaciones, asunto, mensaje y contexto de los tickets, y el ejemplo
 * de las brechas (fundiendo las que queden iguales y borrando las que solo
 * eran el secreto). Una fila cuyo texto no cambia no se toca.
 */
public class V17__redactar_las_claves_anunciadas extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        new V10__redactar_datos_sensibles_guardados().migrate(context);
    }
}
