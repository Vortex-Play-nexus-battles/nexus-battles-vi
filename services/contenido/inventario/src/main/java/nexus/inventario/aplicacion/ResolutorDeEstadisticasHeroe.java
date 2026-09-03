package nexus.inventario.aplicacion;

import nexus.inventario.dominio.EstadisticasHeroe;

/**
 * Puerto para resolver las estadisticas base (nivel 1) de un prototipo de
 * heroe. La implementacion HTTP real consumiria
 * {@code GET /api/v1/heroes/{nombre}} (heroes.yaml, ya mergeado en develop
 * via PR #177), siguiendo el mismo patron que ClienteHeroesHttp en
 * motor-combate. Aqui solo se declara el puerto; el adaptador HTTP para
 * inventario todavia no existe y no es parte de este diseño.
 */
public interface ResolutorDeEstadisticasHeroe {

    EstadisticasHeroe resolver(String prototipo);
}
