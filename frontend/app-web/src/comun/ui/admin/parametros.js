/**
 * Los parámetros de la plataforma, con nombre y explicación de producto — SA-2.
 *
 * El servicio de parámetros publica cada valor con su clave técnica
 * («sanciones.apelacion.plazo-dias») y una descripción de trabajo, sin
 * tildes y con referencias internas. El informe de revisión (puntos 15 y 22)
 * pidió que la consola los nombrara como un producto: aquí está, para cada
 * clave, el nombre corto que se lee en una tabla y una línea que dice para qué
 * sirve. Una clave que este catálogo no conoce se enseña con su descripción
 * del servicio: es preferible a esconderla.
 *
 * @module comun/ui/admin/parametros
 */

/**
 * @typedef {{nombre: string, ayuda: string, grupo: string}} FichaDeParametro
 */

/** Grupos, en el orden en que se leen. */
export const GRUPOS_DE_PARAMETROS = Object.freeze([
  'Sanciones y moderación',
  'Partidas',
  'Torneos',
  'Subastas',
  'Chat',
  'Jugadores nuevos',
  'Tienda',
  'Plataforma',
]);

/** @type {Readonly<Record<string, FichaDeParametro>>} */
export const CATALOGO_DE_PARAMETROS = Object.freeze({
  'sanciones.suspension.minima-horas': {
    nombre: 'Suspensión mínima',
    ayuda: 'La duración más corta que puede tener una suspensión temporal.',
    grupo: 'Sanciones y moderación',
  },
  'sanciones.suspension.maxima-dias': {
    nombre: 'Suspensión máxima',
    ayuda: 'La duración más larga que puede tener una suspensión temporal.',
    grupo: 'Sanciones y moderación',
  },
  'sanciones.apelacion.plazo-dias': {
    nombre: 'Plazo para apelar',
    ayuda: 'Días que tiene un jugador, desde la sanción, para presentar su apelación.',
    grupo: 'Sanciones y moderación',
  },
  'metricas.umbral-sanciones-por-dia': {
    nombre: 'Alerta de sanciones por día',
    ayuda:
      'A partir de cuántas sanciones en un mismo día se avisa de una frecuencia inusual. Sin valor, no se avisa.',
    grupo: 'Sanciones y moderación',
  },
  'metricas.umbral-reportes-por-dia': {
    nombre: 'Alerta de reportes por día',
    ayuda:
      'A partir de cuántos reportes de comentarios en un mismo día se avisa de una frecuencia inusual. Sin valor, se muestran los conteos y no se avisa.',
    grupo: 'Sanciones y moderación',
  },
  'salas.participantes-maximo': {
    nombre: 'Jugadores por batalla',
    ayuda: 'Cuántos participantes admite, como máximo, una batalla.',
    grupo: 'Partidas',
  },
  'salas.partidas.segundos-por-turno': {
    nombre: 'Tiempo por turno',
    ayuda: 'Segundos que tiene cada participante para jugar su turno. Sin valor, no hay límite.',
    grupo: 'Partidas',
  },
  'salas.apuestas.si-gana-la-maquina': {
    nombre: 'Apuestas si gana la máquina',
    ayuda: 'Qué pasa con los créditos apostados cuando una partida la gana un oponente de la IA.',
    grupo: 'Partidas',
  },
  'partidas.creditos.victoria-uno-a-uno': {
    nombre: 'Créditos por victoria 1 contra 1',
    ayuda: 'Lo que gana el vencedor de una partida individual.',
    grupo: 'Partidas',
  },
  'partidas.creditos.victoria-grupal': {
    nombre: 'Créditos por victoria grupal',
    ayuda: 'Lo que gana el vencedor de una partida de varios jugadores.',
    grupo: 'Partidas',
  },
  'partidas.creditos.participacion': {
    nombre: 'Créditos por participar',
    ayuda: 'Lo que recibe cada participante al terminar una partida.',
    grupo: 'Partidas',
  },
  'torneos.dias-entre-torneos': {
    nombre: 'Días entre torneos',
    ayuda: 'Cada cuánto se convoca un torneo.',
    grupo: 'Torneos',
  },
  'torneos.cupos': {
    nombre: 'Equipos por torneo',
    ayuda: 'Cuántos equipos compiten en cada torneo.',
    grupo: 'Torneos',
  },
  'torneos.integrantes-por-equipo': {
    nombre: 'Jugadores por equipo',
    ayuda: 'Cuántos jugadores forman un equipo de torneo.',
    grupo: 'Torneos',
  },
  'torneos.costo-inscripcion-por-defecto': {
    nombre: 'Inscripción sugerida',
    ayuda: 'El costo de inscripción que se propone al crear un torneo.',
    grupo: 'Torneos',
  },
  'subastas.incremento-minimo': {
    nombre: 'Incremento mínimo entre pujas',
    ayuda: 'Cuánto tiene que superar una puja a la anterior.',
    grupo: 'Subastas',
  },
  'subastas.max-subastas-activas-por-jugador': {
    nombre: 'Subastas activas por jugador',
    ayuda: 'Cuántas subastas puede tener abiertas un jugador a la vez.',
    grupo: 'Subastas',
  },
  'subastas.max-pujas-activas-por-jugador': {
    nombre: 'Pujas activas por jugador',
    ayuda: 'En cuántas subastas puede estar pujando un jugador a la vez.',
    grupo: 'Subastas',
  },
  'subastas.intervalo-minimo-segundos': {
    nombre: 'Espera entre pujas',
    ayuda: 'Segundos que tiene que esperar un jugador entre dos pujas suyas.',
    grupo: 'Subastas',
  },
  'chat.historial.tamano': {
    nombre: 'Historial del chat',
    ayuda: 'Cuántos mensajes anteriores se cargan al entrar a un chat.',
    grupo: 'Chat',
  },
  'chat.mensajes-por-minuto': {
    nombre: 'Mensajes por minuto',
    ayuda: 'Cuántos mensajes puede enviar un jugador por minuto. Sin valor, no hay límite.',
    grupo: 'Chat',
  },
  'jugador.creditos-iniciales': {
    nombre: 'Créditos de bienvenida',
    ayuda: 'Los créditos con los que empieza una cuenta nueva.',
    grupo: 'Jugadores nuevos',
  },
  'jugador.kit-inicial': {
    nombre: 'Kit inicial',
    ayuda: 'Los productos que recibe una cuenta nueva: un héroe y su equipamiento.',
    grupo: 'Jugadores nuevos',
  },
  'tienda.tasa-cop-usd': {
    nombre: 'Tasa de cambio a dólares',
    ayuda: 'Pesos colombianos por cada dólar, para mostrar y cobrar en USD. Sin valor, solo pesos.',
    grupo: 'Tienda',
  },
  'tienda.tasa-cop-eur': {
    nombre: 'Tasa de cambio a euros',
    ayuda: 'Pesos colombianos por cada euro, para mostrar y cobrar en EUR. Sin valor, solo pesos.',
    grupo: 'Tienda',
  },
  'plataforma.latencia-objetivo-ms': {
    nombre: 'Latencia objetivo',
    ayuda: 'El tiempo de respuesta que la plataforma se compromete a no superar.',
    grupo: 'Plataforma',
  },
  'plataforma.disponibilidad-objetivo-porcentaje': {
    nombre: 'Disponibilidad objetivo',
    ayuda: 'El porcentaje del mes que la plataforma se compromete a estar disponible.',
    grupo: 'Plataforma',
  },
  'plataforma.cpu-autoescalado-porcentaje': {
    nombre: 'Umbral de autoescalado',
    ayuda: 'El uso de procesador a partir del cual se añade capacidad.',
    grupo: 'Plataforma',
  },
});

/**
 * La ficha de un parámetro: la del catálogo o, si no la conoce, una con su
 * clave y la descripción que publique el servicio.
 *
 * @param {{clave?: string, descripcion?: string}} parametro
 * @returns {FichaDeParametro}
 */
export function fichaDeParametro(parametro) {
  const clave = String(parametro?.clave ?? '');
  return (
    CATALOGO_DE_PARAMETROS[clave] ?? {
      nombre: clave || 'Parámetro sin nombre',
      ayuda: String(parametro?.descripcion ?? ''),
      grupo: 'Otros',
    }
  );
}

/** Unidades del servicio → como se leen. */
const UNIDADES = Object.freeze({
  horas: 'horas',
  dias: 'días',
  creditos: 'créditos',
  mensajes: 'mensajes',
  'mensajes/min': 'mensajes por minuto',
  'sanciones/dia': 'sanciones por día',
  'reportes/dia': 'reportes por día',
  segundos: 'segundos',
  subastas: 'subastas',
  pujas: 'pujas',
  equipos: 'equipos',
  jugadores: 'jugadores',
  ms: 'ms',
  '%': '%',
  'COP/USD': 'COP por USD',
  'COP/EUR': 'COP por EUR',
});

/** Las opciones de los parámetros de texto, como se leen. */
const OPCIONES_LEGIBLES = Object.freeze({
  LIBERAR: 'Se devuelven',
  CONSUMIR: 'Se pierden',
});

/**
 * El valor vigente con su unidad, para leer: «30 días», «Sin definir».
 *
 * @param {{valor?: string|null, unidad?: string|null}} parametro
 * @returns {string}
 */
export function valorLegible(parametro) {
  const valor = parametro?.valor;
  if (valor === null || valor === undefined || String(valor).trim() === '') {
    return 'Sin definir';
  }
  const unidad = parametro?.unidad ? (UNIDADES[parametro.unidad] ?? parametro.unidad) : '';
  const texto = OPCIONES_LEGIBLES[String(valor)] ?? String(valor);
  return unidad ? `${texto} ${unidad}` : texto;
}
