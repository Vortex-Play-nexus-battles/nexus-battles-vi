/**
 * Panel de observabilidad — HU-REN-002 (CA-03) y HU-REN-003 (CP-01).
 *
 * CA-03 pide que «las metricas separadas para carga de listados y propagacion
 * de pujas esten visibles, graficadas y listas para exportarse al informe».
 * Esta pantalla cubre la parte visible y la exportacion; la propagacion de
 * pujas no aparece porque todavia no existe (ver el README del modulo).
 *
 * **No calcula nada.** Percentiles, desglose y veredicto vienen del backend.
 *
 * Sigue `shared/ui-kit/MAPEO-ERRORES.md`:
 * - §5.1 el fallo de la vista entera es `Estado de vista` con reintentar;
 * - §2 se decide por `type`, nunca por el texto.
 *
 * @module plataforma/metricas-plataforma/panel-metricas
 */

import { ErrorDeMetricas, obtenerInforme } from './cliente-metricas.js';

/** Etiquetas legibles de cada tipo. El backend manda el identificador. */
const NOMBRES_DE_TIPO = {
  lectura: 'Lecturas · listados',
  escritura: 'Escrituras · pujas',
  otra: 'Otras',
};

/**
 * @param {string} tipo
 * @returns {string}
 */
function nombreDeTipo(tipo) {
  return NOMBRES_DE_TIPO[tipo] ?? tipo;
}

/**
 * Pinta el estado de carga.
 * @param {HTMLElement} contenedor
 */
export function pintarCargando(contenedor) {
  contenedor.replaceChildren();
  const estado = document.createElement('div');
  estado.className = 'estado-vista estado-vista--cargando';
  estado.setAttribute('role', 'status');
  estado.setAttribute('aria-live', 'polite');
  estado.dataset.estado = 'cargando';

  const titulo = document.createElement('p');
  titulo.className = 'estado-vista__titulo';
  titulo.textContent = 'Cargando el informe de latencia…';
  estado.append(titulo);

  for (let i = 0; i < 3; i++) {
    const hueso = document.createElement('div');
    hueso.className = i === 0 ? 'esqueleto esqueleto--largo' : 'esqueleto';
    estado.append(hueso);
  }

  contenedor.append(estado);
}

/**
 * Pinta un fallo de la vista entera, con reintentar (MAPEO-ERRORES §5.1).
 * @param {HTMLElement} contenedor
 * @param {ErrorDeMetricas|Error} error
 * @param {() => void} alReintentar
 */
export function pintarError(contenedor, error, alReintentar) {
  contenedor.replaceChildren();

  const esDecisionPendiente = error instanceof ErrorDeMetricas && error.esPercentilNoAcordado();

  const estado = document.createElement('div');
  estado.className = 'estado-vista';
  estado.setAttribute('role', 'alert');
  estado.dataset.estado = esDecisionPendiente ? 'sin-percentil' : 'error';

  const titulo = document.createElement('p');
  titulo.className = 'estado-vista__titulo';

  const cuerpo = document.createElement('p');

  if (esDecisionPendiente) {
    // No es un fallo: es una decision de negocio pendiente. Pintarlo en rojo
    // con "reintentar" mandaria a alguien a pulsar un boton que no arregla
    // nada. Lo que hace falta es la aprobacion del Product Owner.
    titulo.textContent = 'Falta acordar el percentil de evaluacion';
    // `!== null` y no `!= null`: ErrorDeMetricas normaliza el campo ausente a
    // null en su constructor, asi que nunca llega undefined y la comparacion
    // estricta cubre exactamente los mismos casos.
    cuerpo.textContent =
      error.muestrasAcumuladas !== null
        ? `La medicion sigue activa: ${error.muestrasAcumuladas} muestras acumuladas. ` +
          'El informe se publica en cuanto el Product Owner apruebe p95 o p99.'
        : 'La medicion sigue activa. El informe se publica en cuanto se apruebe el percentil.';
    estado.append(titulo, cuerpo);
    if (error.criterio) {
      const criterio = document.createElement('p');
      criterio.className = 'estado-vista__criterio';
      criterio.textContent = `Criterio pendiente: ${error.criterio}`;
      estado.append(criterio);
    }
  } else if (error instanceof ErrorDeMetricas && error.esFaltaDePermiso()) {
    // #527: la observabilidad es de administracion. No es un fallo del
    // servicio y no se ofrece reintentar: reintentar no cambia el rol.
    estado.dataset.estado = 'sin-permiso';
    const { titulo: t, detalle } = error.avisoDePermiso;
    titulo.textContent = t;
    cuerpo.textContent = detalle;
    estado.append(titulo, cuerpo);
  } else {
    titulo.textContent = 'No se pudo cargar el informe de latencia';
    cuerpo.textContent = error?.message ?? 'El servicio de metricas no respondio.';
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'boton boton--secundario';
    boton.dataset.accion = 'reintentar';
    boton.textContent = 'Reintentar';
    boton.addEventListener('click', () => {
      if (typeof alReintentar === 'function') {
        alReintentar();
      }
    });
    estado.append(titulo, cuerpo, boton);
  }

  contenedor.append(estado);
}

/**
 * Construye la tabla del desglose por tipo de operacion.
 * @param {Array<{tipo: string, muestras: number, percentilMs: number, maximoMs: number}>} porTipo
 * @param {string} percentil
 * @returns {HTMLElement}
 */
function tablaPorTipo(porTipo, percentil) {
  const tabla = document.createElement('table');
  tabla.className = 'tabla-metricas';
  tabla.dataset.tabla = 'por-tipo';

  const encabezado = document.createElement('thead');
  encabezado.innerHTML =
    '<tr><th scope="col">Tipo de operacion</th>' +
    `<th scope="col">${percentil}</th>` +
    '<th scope="col">Maximo</th>' +
    '<th scope="col">Muestras</th></tr>';

  const cuerpo = document.createElement('tbody');
  for (const fila of porTipo) {
    const tr = document.createElement('tr');
    tr.dataset.tipo = fila.tipo;

    const th = document.createElement('th');
    th.scope = 'row';
    const distintivo = document.createElement('span');
    distintivo.className = 'distintivo';
    distintivo.dataset.tipoOperacion = fila.tipo;
    distintivo.textContent = nombreDeTipo(fila.tipo);
    th.append(distintivo);

    tr.append(
      th,
      celda(`${fila.percentilMs} ms`),
      celda(`${fila.maximoMs} ms`),
      celda(String(fila.muestras)),
    );
    cuerpo.append(tr);
  }

  tabla.append(encabezado, cuerpo);
  return tabla;
}

/**
 * @param {string} texto
 * @returns {HTMLElement}
 */
function celda(texto) {
  const td = document.createElement('td');
  td.textContent = texto;
  return td;
}

/**
 * Construye la tabla de operaciones mas lentas.
 * @param {Array<{metodo: string, ruta: string, muestras: number, percentilMs: number}>} operaciones
 * @param {string} percentil
 * @returns {HTMLElement}
 */
function tablaOperaciones(operaciones, percentil) {
  const tabla = document.createElement('table');
  tabla.className = 'tabla-metricas';
  tabla.dataset.tabla = 'operaciones';

  const encabezado = document.createElement('thead');
  encabezado.innerHTML =
    '<tr><th scope="col">Operacion</th>' +
    `<th scope="col">${percentil}</th>` +
    '<th scope="col">Muestras</th></tr>';

  const cuerpo = document.createElement('tbody');
  for (const operacion of operaciones) {
    const tr = document.createElement('tr');
    const th = document.createElement('th');
    th.scope = 'row';
    th.textContent = `${operacion.metodo} ${operacion.ruta}`;
    tr.append(th, celda(`${operacion.percentilMs} ms`), celda(String(operacion.muestras)));
    cuerpo.append(tr);
  }

  tabla.append(encabezado, cuerpo);
  return tabla;
}

/**
 * Pinta el informe.
 * @param {HTMLElement} contenedor
 * @param {object} informe
 */
export function pintarInforme(contenedor, informe) {
  contenedor.replaceChildren();

  const tarjeta = document.createElement('section');
  tarjeta.className = 'tarjeta';
  tarjeta.dataset.estado = informe.sinDatos ? 'sin-datos' : 'con-datos';

  const titulo = document.createElement('h2');
  titulo.textContent = `Latencia de ${informe.servicio}`;
  tarjeta.append(titulo);

  if (informe.sinDatos) {
    // No medir no es lo mismo que cumplir. Si esto se pintara como un verde
    // con ceros, un servicio que nunca se instrumento pareceria el mas rapido.
    const vacio = document.createElement('p');
    vacio.className = 'estado-vista__titulo';
    vacio.textContent =
      'Sin muestras todavia: el servicio no ha atendido peticiones desde el ultimo arranque. ' +
      'No se puede afirmar que cumpla, ni que incumpla.';
    tarjeta.append(vacio);
    contenedor.append(tarjeta);
    return;
  }

  const resumen = document.createElement('p');
  resumen.dataset.campo = 'resumen';
  resumen.textContent =
    `${informe.percentil}: ${informe.percentilMs} ms · ` +
    `maximo ${informe.maximoMs} ms · ${informe.muestras} muestras`;

  const veredicto = document.createElement('p');
  veredicto.className = informe.cumple ? 'aviso aviso--exito' : 'aviso aviso--error';
  veredicto.dataset.campo = 'veredicto';
  veredicto.textContent = informe.cumple
    ? `Dentro del objetivo configurado (${informe.objetivoMs} ms)`
    : `Por encima del objetivo configurado (${informe.objetivoMs} ms)`;

  // El objetivo se nombra "configurado" y no "umbral de HU-REN-002" a
  // proposito: esa historia no define ningun numero propio. El que se muestra
  // es el de RNF-REN-001, que es el unico que existe en los requisitos.
  const referencia = document.createElement('p');
  referencia.className = 'campo__pista';
  referencia.dataset.campo = 'referencia-objetivo';
  referencia.textContent =
    `Objetivo de referencia: ${informe.objetivoMs} ms, configurado desde RNF-REN-001. ` +
    'HU-REN-002 no define un umbral propio.';

  tarjeta.append(resumen, veredicto, referencia);

  if (Array.isArray(informe.porTipo) && informe.porTipo.length > 0) {
    const subtitulo = document.createElement('h3');
    subtitulo.textContent = 'Lecturas y escrituras por separado';
    tarjeta.append(subtitulo, tablaPorTipo(informe.porTipo, informe.percentil));
  }

  if (Array.isArray(informe.operacionesMasLentas) && informe.operacionesMasLentas.length > 0) {
    const subtitulo = document.createElement('h3');
    subtitulo.textContent = 'Operaciones mas lentas';
    tarjeta.append(subtitulo, tablaOperaciones(informe.operacionesMasLentas, informe.percentil));
  }

  contenedor.append(tarjeta);
}

/**
 * El informe en texto plano, para pegarlo en el acta.
 *
 * Se arma con los mismos datos que ya se pintaron: no se vuelve a pedir nada
 * al servidor, asi que lo exportado es exactamente lo que la persona esta
 * viendo en pantalla.
 *
 * @param {object} informe
 * @returns {string}
 */
export function informeComoTexto(informe) {
  const lineas = [
    `Informe de latencia — ${informe.servicio}`,
    `Objetivo de referencia: ${informe.objetivoMs} ms (RNF-REN-001)`,
    `Muestras: ${informe.muestras}`,
  ];

  if (informe.sinDatos) {
    lineas.push('SIN DATOS: no se puede afirmar que cumpla ni que incumpla.');
    return lineas.join('\n');
  }

  lineas.push(
    `${informe.percentil}: ${informe.percentilMs} ms`,
    `Maximo: ${informe.maximoMs} ms`,
    `Resultado: ${informe.cumple ? 'CUMPLE' : 'NO CUMPLE'}`,
    '',
    'Por tipo de operacion:',
  );

  for (const fila of informe.porTipo ?? []) {
    lineas.push(
      `  ${fila.tipo}: ${fila.percentilMs} ms (maximo ${fila.maximoMs} ms, ${fila.muestras} muestras)`,
    );
  }

  return lineas.join('\n');
}

/**
 * Arranca el panel: pinta cargando, pide el informe y pinta el resultado.
 *
 * @param {HTMLElement} contenedor
 * @param {{obtener?: typeof obtenerInforme}} [opciones]
 * @returns {Promise<void>}
 */
export async function iniciarPanel(contenedor, opciones = {}) {
  if (!contenedor) {
    throw new Error('panel-metricas: hace falta un contenedor donde pintar.');
  }
  const obtener = opciones.obtener ?? obtenerInforme;

  pintarCargando(contenedor);
  try {
    const informe = await obtener();
    pintarInforme(contenedor, informe);
    contenedor.dataset.ultimoInforme = informeComoTexto(informe);
  } catch (error) {
    pintarError(contenedor, error, () => iniciarPanel(contenedor, opciones));
  }
}
