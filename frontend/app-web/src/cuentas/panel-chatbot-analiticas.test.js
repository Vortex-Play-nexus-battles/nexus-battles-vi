/**
 * Pestaña de analíticas del panel del asistente — HU-CHA-012 (RF-CHA-012).
 */

import { jest } from '@jest/globals';

import { ErrorDelChatbot } from '../comun/cliente-chatbot.js';
import {
  diaEnZona,
  duracion,
  graficaDeTendencia,
  horasDeAtencion,
  montarAnaliticas,
  nombreDeCategoria,
  resumenDeTendencia,
  textoDeErrorDeAnaliticas,
  textoDeErrorDelPanel,
  zonaLegible,
} from './panel-chatbot-analiticas.js';

const esperar = () => new Promise((resolver) => setTimeout(resolver, 0));

const TABLERO = {
  desde: '2026-09-09T05:00:00Z',
  hasta: '2026-09-11T05:00:00Z',
  zonaHoraria: 'America/Bogota',
  conversaciones: 2,
  preguntas: 3,
  respuestasMedidas: 3,
  escalamientos: 1,
  tasaResolucion: 2 / 3,
  tiempoRespuestaPromedioMs: 1520,
  calificaciones: 0,
  calificacionesUtiles: 0,
  satisfaccion: null,
  temasFrecuentes: [{ clave: 'k', titulo: 'Subastas', respuestas: 2 }],
  tendencia: [
    { dia: '2026-09-09', conversaciones: 1, preguntas: 1, respuestas: 1, escalamientos: 0 },
    { dia: '2026-09-10', conversaciones: 1, preguntas: 2, respuestas: 2, escalamientos: 1 },
  ],
};

function montar(cliente) {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  const descargarArchivo = jest.fn();
  const pestana = montarAnaliticas(raiz, { cliente, descargarArchivo });
  return { raiz, pestana, descargarArchivo };
}

test('diaEnZona respeta la zona y el hasta exclusivo', () => {
  expect(diaEnZona('2026-09-09T05:00:00Z', 'America/Bogota')).toBe('2026-09-09');
  expect(diaEnZona('2026-09-11T05:00:00Z', 'America/Bogota', -1)).toBe('2026-09-10');
});

test('duracion usa ms por debajo de un segundo', () => {
  expect(duracion(null)).toBe('—');
  expect(duracion(250)).toBe('250 ms');
  expect(duracion(1520)).toMatch(/^1[,.]5 s$/);
});

test('la gráfica tiene una barra por día, proporcional, y dice su valor', () => {
  const svg = graficaDeTendencia(TABLERO.tendencia);
  const barras = svg.querySelectorAll('.grafica-tendencia__barra');

  expect(barras).toHaveLength(2);
  expect(Number(barras[1].getAttribute('height'))).toBeGreaterThan(
    Number(barras[0].getAttribute('height')),
  );
  expect(barras[1].querySelector('title').textContent).toContain('2 preguntas');
  expect(svg.getAttribute('role')).toBe('img');
});

test('sin fechas pide el período por omisión, lo muestra en el filtro y pinta el tablero', async () => {
  const cliente = { analiticas: jest.fn(async () => TABLERO) };
  const { raiz, pestana } = montar(cliente);

  await pestana.cargar();

  expect(cliente.analiticas).toHaveBeenCalledWith({ desde: null, hasta: null });
  expect(raiz.querySelector('input[name="desde"]').value).toBe('2026-09-09');
  expect(raiz.querySelector('input[name="hasta"]').value).toBe('2026-09-10');
  const cifras = [
    ...raiz.querySelector('.panel-chatbot__cifras').querySelectorAll('.metrica__valor'),
  ].map((nodo) => nodo.textContent);
  // Sin `tickets` (servicio anterior a 1.3.7) no hay cifra de solicitudes.
  expect(cifras).toEqual(['2', '3', '67 %', '1', '—', expect.stringMatching(/s$/)]);
  expect(raiz.textContent).toContain('Nadie calificó respuestas en el período');
  expect(raiz.querySelectorAll('.grafica-tendencia__barra')).toHaveLength(2);
  expect(raiz.querySelector('[data-zona="temas"] tbody').textContent).toContain('Subastas');
});

test('sin actividad no dibuja barras vacías', async () => {
  const quieto = {
    ...TABLERO,
    tendencia: TABLERO.tendencia.map((punto) => ({ ...punto, preguntas: 0 })),
    temasFrecuentes: [],
  };
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => quieto) });

  await pestana.cargar();

  expect(raiz.querySelector('.grafica-tendencia')).toBeNull();
  expect(raiz.textContent).toContain('Sin preguntas en este período');
});

test('un período al revés no consulta al servicio', async () => {
  const cliente = { analiticas: jest.fn(async () => TABLERO) };
  const { raiz, pestana } = montar(cliente);
  raiz.querySelector('input[name="desde"]').value = '2026-09-10';
  raiz.querySelector('input[name="hasta"]').value = '2026-09-01';

  await pestana.cargar();

  expect(cliente.analiticas).not.toHaveBeenCalled();
  expect(raiz.textContent).toContain('no puede ser anterior');
});

test('si el servicio falla, lo explica y permite reintentar', async () => {
  const analiticas = jest
    .fn()
    .mockRejectedValueOnce(new ErrorDelChatbot(null, 0))
    .mockResolvedValueOnce(TABLERO);
  const { raiz, pestana } = montar({ analiticas });

  await pestana.cargar();
  expect(raiz.textContent).toContain('El servicio del asistente no responde');

  raiz.querySelector('[data-accion="reintentar"]').click();
  await esperar();
  expect(analiticas).toHaveBeenCalledTimes(2);
  expect(raiz.querySelectorAll('.metrica').length).toBeGreaterThan(0);
});

test('Descargar CSV entrega el archivo del servicio', async () => {
  const archivo = { nombre: 'analiticas.csv', contenido: new Blob(['x']) };
  const cliente = {
    analiticas: jest.fn(async () => TABLERO),
    exportarAnaliticas: jest.fn(async () => archivo),
  };
  const { raiz, descargarArchivo } = montar(cliente);
  raiz.querySelector('input[name="desde"]').value = '2026-09-01';

  raiz.querySelector('[data-accion="exportar-analiticas"]').click();
  await esperar();

  expect(cliente.exportarAnaliticas).toHaveBeenCalledWith({ desde: '2026-09-01', hasta: null });
  expect(descargarArchivo).toHaveBeenCalledWith(archivo);
});

test('textoDeErrorDeAnaliticas: un 400 es el período; lo demás dice qué falló y por qué', () => {
  expect(textoDeErrorDeAnaliticas(new ErrorDelChatbot({ title: 'Bad Request' }, 400)).titulo).toBe(
    'El período no es válido',
  );
  const caido = textoDeErrorDeAnaliticas(new ErrorDelChatbot(null, 0));
  expect(caido.titulo).toBe('No se pudieron cargar las analíticas');
  expect(caido.detalle).toContain('no responde');
  const sinPermiso = textoDeErrorDeAnaliticas(new ErrorDelChatbot(null, 403));
  expect(sinPermiso.titulo).toBe('No se pudieron cargar las analíticas');
  expect(sinPermiso.detalle).toContain('permiso');
  expect(
    textoDeErrorDeAnaliticas(new ErrorDelChatbot(null, 500), 'No se pudo descargar el CSV').titulo,
  ).toBe('No se pudo descargar el CSV');
});

test('textoDeErrorDelPanel se sigue pudiendo importar desde la pestaña de analíticas', () => {
  expect(textoDeErrorDelPanel(new ErrorDelChatbot(null, 0), 'No se pudo X').titulo).toBe(
    'No se pudo X',
  );
});

test('si la descarga del CSV falla, dice que falló la descarga y por qué', async () => {
  const cliente = {
    analiticas: jest.fn(async () => TABLERO),
    exportarAnaliticas: jest.fn(async () => {
      throw new ErrorDelChatbot(null, 503);
    }),
  };
  const { raiz, descargarArchivo } = montar(cliente);

  raiz.querySelector('[data-accion="exportar-analiticas"]').click();
  await esperar();

  expect(descargarArchivo).not.toHaveBeenCalled();
  expect(raiz.querySelector('.panel-chatbot__nota').textContent).toBe(
    'No se pudo descargar el CSV. El servicio del asistente no responde. Vuelve a intentarlo en unos minutos.',
  );
});

// 1.7.0 (7.4.7): fórmulas explicadas, resultado de cada respuesta, preguntas
// frecuentes y tendencia de las palabras.
const COMPLETO = {
  ...TABLERO,
  conversaciones: 3,
  conversacionesConActividad: 2,
  preguntas: 9,
  respuestasMedidas: 8,
  escalamientos: 2,
  respuestasClasificadas: 7,
  respuestasResueltas: 3,
  tasaResolucion: 3 / 7,
  resultados: [
    { resultado: 'TEMA', respuestas: 2 },
    { resultado: 'CONSULTA_EN_VIVO', respuestas: 1 },
    { resultado: 'NAVEGACION', respuestas: 0 },
    { resultado: 'NO_DISPONIBLE', respuestas: 1 },
    { resultado: 'ESCALADO', respuestas: 2 },
    { resultado: 'NO_SIRVIO', respuestas: 1 },
    { resultado: 'PIDE_REFORMULAR', respuestas: 0 },
  ],
  preguntasFrecuentes: [{ texto: 'como pujo en una subasta', preguntas: 3, conversaciones: 3 }],
  tickets: {
    total: 1,
    abiertos: 1,
    enProceso: 0,
    resueltos: 0,
    cerrados: 0,
    horasPromedioDeAtencion: null,
    porCategoria: [{ categoria: 'SOPORTE_TECNICO', tickets: 1 }],
  },
  palabrasClave: [
    {
      palabra: 'subasta',
      preguntas: 3,
      conversaciones: 3,
      preguntasPeriodoAnterior: 1,
      tendencia: 'SUBE',
    },
    {
      palabra: 'pujo',
      preguntas: 3,
      conversaciones: 3,
      preguntasPeriodoAnterior: 0,
      tendencia: 'NUEVA',
    },
  ],
};

test('1.7.0: cada cifra dice lo que cuenta, y lo que ofreció el asistente va aparte de las solicitudes', async () => {
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => COMPLETO) });

  await pestana.cargar();

  const tarjetas = [
    ...raiz.querySelector('.panel-chatbot__cifras').querySelectorAll('.metrica'),
  ].map((tarjeta) => tarjeta.textContent);
  expect(tarjetas).toHaveLength(7);
  expect(tarjetas[0]).toContain('Conversaciones iniciadas');
  expect(tarjetas[0]).toContain('2 con preguntas en el período');
  expect(tarjetas[2]).toContain('43 %');
  expect(tarjetas[2]).toContain('3 de 7 resueltas sin una persona del equipo');
  expect(tarjetas[3]).toContain('Ofreció soporte');
  expect(tarjetas[4]).toContain('Solicitudes de soporte');
  // Que el asistente ofrezca soporte no es que lo atendiera una persona.
  expect(raiz.textContent).not.toMatch(/human/i);
});

test('1.7.0: muestra cómo terminaron las respuestas y cuáles cuentan como resueltas', async () => {
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => COMPLETO) });

  await pestana.cargar();

  const filas = [...raiz.querySelectorAll('[data-zona="tabla-resultados"] tbody tr')].map((fila) =>
    [...fila.querySelectorAll('td')].map((celda) => celda.textContent),
  );
  expect(filas[0]).toEqual(['Respondió con un tema de la base', '2', 'Resuelta']);
  expect(filas[2]).toEqual(['Llevó a la sección pedida', '0', 'Resuelta']);
  expect(filas[4]).toEqual(['No entendió y ofreció soporte', '2', 'No resuelta']);
  expect(filas).toHaveLength(7);
});

test('1.7.0: las preguntas frecuentes y la tendencia de las palabras', async () => {
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => COMPLETO) });

  await pestana.cargar();

  expect(raiz.querySelector('[data-zona="tabla-preguntas-frecuentes"] tbody tr').textContent).toBe(
    'como pujo en una subasta33',
  );
  const encabezados = [...raiz.querySelectorAll('[data-zona="palabras-clave"] thead th')].map(
    (celda) => celda.textContent,
  );
  expect(encabezados).toEqual([
    'Palabra',
    'Preguntas',
    'Conversaciones',
    'Período anterior',
    'Tendencia',
  ]);
  const palabras = [...raiz.querySelectorAll('[data-zona="palabras-clave"] tbody tr')].map(
    (fila) => fila.textContent,
  );
  expect(palabras).toEqual(['subasta331Sube', 'pujo330Nueva']);
});

test('1.7.0: sin preguntas repetidas ni resultados guardados lo dice', async () => {
  const sinDatos = {
    ...COMPLETO,
    tasaResolucion: null,
    respuestasClasificadas: 0,
    respuestasResueltas: 0,
    resultados: COMPLETO.resultados.map((fila) => ({ ...fila, respuestas: 0 })),
    preguntasFrecuentes: [],
  };
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => sinDatos) });

  await pestana.cargar();

  expect(raiz.querySelector('[data-zona="resultados"]').textContent).toContain(
    'Ninguna respuesta del período guardó su resultado',
  );
  expect(raiz.querySelector('[data-zona="preguntas-frecuentes"]').textContent).toContain(
    'Ninguna pregunta se repitió lo suficiente',
  );
  expect(raiz.querySelector('.panel-chatbot__cifras').textContent).toContain('—');
});

// 1.3.7 (7.4.7): solicitudes de soporte y palabras clave.
const CON_SOPORTE = {
  ...TABLERO,
  tickets: {
    total: 4,
    abiertos: 1,
    enProceso: 1,
    resueltos: 1,
    cerrados: 1,
    horasPromedioDeAtencion: 2.5,
    porCategoria: [
      { categoria: 'SOPORTE_TECNICO', tickets: 3 },
      { categoria: 'SUBASTA_Y_COMERCIO', tickets: 1 },
    ],
  },
  palabrasClave: [{ palabra: 'torneo', preguntas: 5, conversaciones: 3 }],
};

test('horasDeAtencion usa minutos por debajo de una hora y — sin datos', () => {
  expect(horasDeAtencion(null)).toBe('—');
  expect(horasDeAtencion(0.5)).toBe('30 min');
  expect(horasDeAtencion(2.5)).toMatch(/^2[,.]5 h$/);
});

test('nombreDeCategoria usa el nombre que ve el jugador', () => {
  expect(nombreDeCategoria('SOPORTE_TECNICO')).toBe('Problema técnico');
  expect(nombreDeCategoria('OTRA_NUEVA')).toBe('OTRA_NUEVA');
});

test('pinta las solicitudes de soporte y las palabras más usadas', async () => {
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => CON_SOPORTE) });

  await pestana.cargar();

  const soporte = raiz.querySelector('[data-zona="soporte"]');
  const cifras = [...soporte.querySelectorAll('.metrica__valor')].map((nodo) => nodo.textContent);
  expect(cifras).toEqual(['4', '2', '2', expect.stringMatching(/h$/)]);
  const categorias = [
    ...soporte.querySelectorAll('[data-zona="soporte-por-categoria"] tbody tr'),
  ].map((fila) => fila.textContent);
  expect(categorias).toEqual(['Problema técnico3', 'Subastas y comercio1']);

  const palabras = raiz.querySelector('[data-zona="palabras-clave"]');
  expect(palabras.querySelector('tbody tr').textContent).toBe('torneo53');
});

test('sin solicitudes ni palabras muestra estados vacíos; sin los campos, no pinta las secciones', async () => {
  const vacio = {
    ...TABLERO,
    tickets: {
      total: 0,
      abiertos: 0,
      enProceso: 0,
      resueltos: 0,
      cerrados: 0,
      horasPromedioDeAtencion: null,
      porCategoria: [],
    },
    palabrasClave: [],
  };
  const conVacios = montar({ analiticas: jest.fn(async () => vacio) });
  await conVacios.pestana.cargar();
  expect(conVacios.raiz.textContent).toContain('Nadie pidió soporte en este período');
  expect(conVacios.raiz.textContent).toContain('Todavía no hay solicitudes atendidas');
  expect(conVacios.raiz.textContent).toContain('Todavía no hay palabras que se repitan');

  const antiguo = montar({ analiticas: jest.fn(async () => TABLERO) });
  await antiguo.pestana.cargar();
  expect(antiguo.raiz.querySelector('[data-zona="soporte"]')).toBeNull();
  expect(antiguo.raiz.querySelector('[data-zona="palabras"]')).toBeNull();
});

/* Punto 25 del informe de revisión del Super Administrador: la tabla de la
   consola, cifras a la derecha, el peso de cada fila y la serie en palabras. */

test('las tablas son las de la consola: cifras a la derecha y la barra del peso de cada fila', async () => {
  const conTemas = {
    ...TABLERO,
    temasFrecuentes: [
      { clave: 'a', titulo: 'Subastas', respuestas: 1200 },
      { clave: 'b', titulo: 'Salas', respuestas: 300 },
    ],
  };
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => conTemas) });
  await pestana.cargar();

  const temas = raiz.querySelector('[data-zona="temas"]');
  expect(temas.tagName).toBe('TABLE');
  expect(temas.classList.contains('tabla-admin__tabla')).toBe(true);
  // El marco desplazable se recorre con el teclado y tiene nombre.
  const marco = temas.closest('.tabla-admin__marco');
  expect(marco.getAttribute('tabindex')).toBe('0');
  expect(document.getElementById(marco.getAttribute('aria-labelledby')).textContent).toBe(
    'Temas más consultados',
  );
  const [encabezadoTema, encabezadoCifra] = temas.querySelectorAll('thead th');
  expect(encabezadoTema.dataset.tipo).toBe('texto');
  expect(encabezadoCifra.dataset.tipo).toBe('numero');

  const [primera, segunda] = temas.querySelectorAll('tbody tr');
  const cifra = primera.querySelectorAll('td')[1];
  expect(cifra.dataset.tipo).toBe('numero');
  expect(cifra.dataset.etiqueta).toBe('Respuestas');
  // La barra es decorativa: la celda sigue diciendo solo la cifra.
  expect(cifra.textContent).toBe('1.200');
  expect(cifra.querySelector('.tabla-panel__barra').getAttribute('aria-hidden')).toBe('true');
  const proporcion = (fila) =>
    Number(fila.querySelector('.tabla-panel__relleno').style.getPropertyValue('--proporcion'));
  expect(proporcion(primera)).toBe(1);
  expect(proporcion(segunda)).toBe(0.25);
  // La celda que nombra la fila no lleva barra ni etiqueta repetida.
  expect(primera.querySelector('td').classList.contains('tabla-admin__principal')).toBe(true);
  expect(primera.querySelector('td').dataset.etiqueta).toBeUndefined();
});

test('una columna con textos no se toma por cifras ni lleva barra', async () => {
  const conPalabras = {
    ...TABLERO,
    tickets: {
      total: 3,
      abiertos: 1,
      enProceso: 0,
      resueltos: 2,
      cerrados: 0,
      horasPromedioDeAtencion: 0.5,
      porCategoria: [{ categoria: 'SOPORTE_TECNICO', tickets: 3 }],
    },
    palabrasClave: [
      { palabra: 'torneo', preguntas: 5, conversaciones: 3 },
      { palabra: 'subasta', preguntas: 2, conversaciones: 2 },
    ],
  };
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => conPalabras) });
  await pestana.cargar();

  const palabras = raiz.querySelector('[data-zona="palabras-clave"]');
  const [palabra, preguntas, conversaciones] = palabras.querySelectorAll('thead th');
  expect([palabra.dataset.tipo, preguntas.dataset.tipo, conversaciones.dataset.tipo]).toEqual([
    'texto',
    'numero',
    'numero',
  ]);
  // Solo la primera columna de cifras lleva la barra.
  const fila = palabras.querySelector('tbody tr');
  expect(fila.querySelectorAll('.tabla-panel__barra')).toHaveLength(1);
  expect(fila.querySelectorAll('td')[1].querySelector('.tabla-panel__barra')).not.toBeNull();
  expect(raiz.querySelector('[data-zona="soporte-por-categoria"] tbody tr').textContent).toBe(
    'Problema técnico3',
  );
});

test('la gráfica dice el total, el día con más preguntas y la zona horaria en palabras', async () => {
  const { raiz, pestana } = montar({ analiticas: jest.fn(async () => TABLERO) });
  await pestana.cargar();

  expect(raiz.querySelector('[data-zona="resumen-tendencia"]').textContent).toBe(
    resumenDeTendencia(TABLERO.tendencia),
  );
  expect(resumenDeTendencia(TABLERO.tendencia)).toMatch(
    /^3 preguntas en 2 días · el día con más: /,
  );
  expect(resumenDeTendencia([{ dia: '2026-09-09', preguntas: 1 }])).toMatch(
    /^1 pregunta en 1 día · /,
  );
  expect(raiz.textContent).toContain('en hora de Colombia');
  expect(raiz.textContent).not.toContain('America/Bogota');
  expect(raiz.querySelectorAll('.panel-chatbot__eje span')).toHaveLength(2);
});

test('zonaLegible dice el país o la ciudad, nunca el identificador', () => {
  expect(zonaLegible('America/Bogota')).toBe('Colombia');
  expect(zonaLegible('America/New_York')).toBe('New York');
  expect(zonaLegible(null)).toBeNull();
});
