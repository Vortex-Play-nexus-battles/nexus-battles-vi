/**
 * Accesibilidad de los estados POBLADOS, no de los vacios — FI-R15.
 *
 * ## Por que hacia falta otro fichero
 *
 * `accesibilidad.spec.js` abre las 32 vistas sin servicios y pasa axe. Eso
 * cubre mucho, y deja fuera justo lo que mas riesgo tiene: **ninguna de esas
 * capturas tiene datos**. Una tarjeta de subasta activa, un contador por debajo
 * de diez segundos, un distintivo de rareza, un credito comprometido, una tabla
 * de moderacion con filas, una bandeja con avisos — nada de eso existe en la
 * pantalla vacia, y es donde viven los contrastes raros, los `role` mal puestos
 * y los objetivos tactiles pequenos.
 *
 * Dicho de otra forma: el barrido en verde demostraba que los estados vacios
 * son accesibles. De los poblados no decia nada.
 *
 * ## Como se poblan
 *
 * Interceptando la red con `page.route` y devolviendo cuerpos con la forma del
 * contrato. No se tocan los modulos ni se les inyecta nada: la vista hace sus
 * peticiones de siempre y recibe una respuesta valida. Si un dia el contrato
 * cambia y el cuerpo de aqui deja de encajar, la vista se pintara vacia y estas
 * pruebas dejaran de ejercitar lo que dicen ejercitar — por eso cada escenario
 * afirma primero que lo que queria pintar **esta en la pantalla**, y solo
 * despues pasa axe. Un banco que no llega a pintar nada es un verde que no
 * significa nada, y ese es el fallo que este fichero no puede permitirse.
 *
 * ## Que se exige
 *
 * Lo mismo que el barrido vacio: falla con `serious` y `critical` de WCAG 2.1
 * AA. No se sube el nivel aqui; se amplia la superficie.
 *
 * Son estados representativos de riesgo, no las 33 vistas por 5 anchuras: un
 * barrido exhaustivo con datos costaria minutos de CI y repetiria lo que el
 * barrido vacio ya cubre.
 */

import { AxeBuilder } from '@axe-core/playwright';
import { test, expect } from '@playwright/test';

import { PREFIJO_WEB } from './vistas.js';
import { inyectarSesion, sesionSintetica } from './identidad.js';

const NORMAS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const GRAVES = new Set(['serious', 'critical']);

/** Escritorio y movil: el objetivo tactil y algunos contrastes solo salen en uno. */
const ANCHURAS = Object.freeze([
  { nombre: 'desktop', ancho: 1440, alto: 900 },
  { nombre: 'movil', ancho: 375, alto: 812 },
]);

const json = (cuerpo) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(cuerpo),
});

/** Una subasta a punto de cerrar: contador urgente, rareza y credito en juego. */
function subastaUrgente() {
  return {
    id: 'aaaaaaa1-1111-4111-8111-111111111111',
    nombreProducto: 'Hacha de Obsidiana Fracturada',
    tipoProducto: 'ARMA',
    descripcionCorta: 'Filo negro, mella de guerra.',
    rareza: 'epica',
    vendedorId: 'thar_vex',
    ofertaVigente: '1350',
    precioCompraInmediata: '2100',
    // Ocho segundos: por debajo del umbral de diez que enciende el latido, el
    // color de urgencia y el boton «Ir ahora».
    fechaFin: new Date(Date.now() + 8000).toISOString(),
    cantidadPujas: 7,
    miniaturaUrl: null,
    esMaestroDeJuego: false,
  };
}

function subastaTranquila() {
  return {
    ...subastaUrgente(),
    id: 'aaaaaaa2-2222-4222-8222-222222222222',
    nombreProducto: 'Grebas del Centinela Caido',
    rareza: 'rara',
    ofertaVigente: '880',
    fechaFin: new Date(Date.now() + 3_600_000).toISOString(),
  };
}

/** Una sin rareza, para que el camino neutro de FI-R1 tambien pase por axe. */
function subastaSinRareza() {
  return {
    ...subastaTranquila(),
    id: 'aaaaaaa3-3333-4333-8333-333333333333',
    nombreProducto: 'Objeto sin catalogar',
    rareza: undefined,
  };
}

const ESCENARIOS = [
  {
    id: 'pujas-activas',
    titulo: 'subastas con una puja urgente, rareza y credito comprometido',
    ruta: 'cuentas/pujas.html',
    sesion: () => sesionSintetica({ apodo: 'qa_pujas', rol: 'JUGADOR' }),
    rutas: [
      [
        '**/api/v1/subastas?*',
        json({
          contenido: [subastaUrgente(), subastaTranquila(), subastaSinRareza()],
          pagina: 0,
          tamano: 16,
          totalElementos: 3,
          totalPaginas: 1,
        }),
      ],
      [
        '**/api/v1/mis-pujas/resumen',
        json({ subastasConPuja: 2, creditosRetenidos: '2230', saldoTotal: null }),
      ],
    ],
    // Lo que tiene que haberse pintado para que la prueba signifique algo.
    exige: ['.tarjeta-subasta', '.badge-epica', '.animacion-latido'],
  },
  {
    id: 'batallas-con-salas',
    titulo: 'listado de batallas con salas abiertas, llenas y privadas',
    ruta: 'plataforma/salas-partidas/batallas.html',
    sesion: () => sesionSintetica({ apodo: 'qa_salas', rol: 'JUGADOR' }),
    rutas: [
      [
        '**/api/v1/salas?*',
        json({
          contenido: [
            sala({ id: 'bbbbbbb1-1111-4111-8111-111111111111', ocupacion: 4 }),
            sala({
              id: 'bbbbbbb2-2222-4222-8222-222222222222',
              estado: 'LLENA',
              ocupacion: 6,
            }),
            sala({
              id: 'bbbbbbb3-3333-4333-8333-333333333333',
              estado: 'PRIVADA',
              privada: true,
              ocupacion: 1,
            }),
          ],
          pagina: 0,
          tamano: 12,
          totalElementos: 3,
          totalPaginas: 1,
        }),
      ],
    ],
    exige: ['[data-sala]', '.distintivo--llena', '.distintivo--privada'],
  },
  {
    id: 'tienda-con-catalogo',
    titulo: 'tienda con precios, rebaja y carrito con importes',
    ruta: 'cuentas/tienda.html',
    sesion: () => sesionSintetica({ apodo: 'qa_tienda', rol: 'JUGADOR' }),
    rutas: [
      [
        // R16 — la vitrina se mudó a /api/v1/vitrina (ecommerce-carrito.yaml
        // 1.2.0) y sus ids son los UUID del catálogo maestro. Con la ruta vieja
        // la vista no pintaba nada y el escenario se ponía rojo en `exige`, que
        // es justo para lo que está. La rebaja y el precio ausente ya no los
        // manda la vitrina 1.2.0, pero la tarjeta los sigue sabiendo pintar y
        // su accesibilidad se sigue auditando.
        '**/api/v1/vitrina*',
        json({
          content: [
            producto({ id: 'aaaaaaa1-0000-4000-8000-000000000001', nombre: 'Yelmo del Alba' }),
            producto({
              id: 'aaaaaaa1-0000-4000-8000-000000000002',
              nombre: 'Amuleto de Brasa',
              precioOriginal: 20000,
              precioFinal: 16000,
              enPromocion: true,
              porcentajeDescuento: 20,
            }),
            producto({
              id: 'aaaaaaa1-0000-4000-8000-000000000003',
              nombre: 'Pocion sin precio',
              precioFinal: null,
            }),
          ],
        }),
      ],
      [
        '**/api/v1/carrito',
        json({
          id: 9,
          usuarioId: 'qa',
          total: 36000,
          items: [
            {
              id: 1,
              cantidad: 2,
              precioUnitario: 18000,
              subtotal: 36000,
              producto: { nombre: 'Yelmo del Alba', moneda: 'COP' },
            },
          ],
        }),
      ],
    ],
    // El descuento y el precio ausente son los dos estados que FI-R2 anadio.
    exige: ['.product-card', '.badge-descuento', '.precio-ausente', '.cart-item'],
  },
];

function sala(cambios = {}) {
  return {
    id: 'bbbbbbb1-1111-4111-8111-111111111111',
    estado: 'ABIERTA',
    modalidad: 'HASTA_SEIS',
    maximoParticipantes: 6,
    ocupacion: 4,
    recompensaCreditos: 320,
    incluirHeroeIA: true,
    heroesIA: 1,
    privada: false,
    tamanoEquipo: null,
    idAnfitrion: 'ccccccc1-1111-4111-8111-111111111111',
    participantes: [],
    idPartida: null,
    creadaEn: new Date().toISOString(),
    ...cambios,
  };
}

function producto(cambios = {}) {
  return {
    id: 'aaaaaaa1-0000-4000-8000-000000000001',
    nombre: 'Yelmo del Alba',
    imagenUrl: null,
    descripcion: 'Acero claro, forjado al amanecer.',
    habilidades: 'Defensa +4',
    tipo: 'ARMADURA',
    precioFinal: 18500,
    precioOriginal: 18500,
    moneda: 'COP',
    enPromocion: false,
    porcentajeDescuento: 0,
    esPropio: false,
    enListaDeseos: false,
    ...cambios,
  };
}

for (const escenario of ESCENARIOS) {
  for (const pantalla of ANCHURAS) {
    test(`${escenario.id} · ${pantalla.nombre}: ${escenario.titulo}`, async ({
      browser,
      baseURL,
    }) => {
      const contexto = await browser.newContext({
        viewport: { width: pantalla.ancho, height: pantalla.alto },
        baseURL,
      });
      try {
        await inyectarSesion(contexto, escenario.sesion());
        const pagina = await contexto.newPage();

        for (const [patron, respuesta] of escenario.rutas) {
          await pagina.route(patron, (ruta) => ruta.fulfill(respuesta));
        }
        // El canal en vivo no se simula: la vista tiene que funcionar sin el, y
        // su estado degradado tambien entra en la auditoria.
        await pagina.route('**/ws-subastas/**', (ruta) => ruta.abort());

        await pagina.goto(`/${PREFIJO_WEB}/${escenario.ruta}`, {
          waitUntil: 'domcontentloaded',
        });

        // Primero: que el banco haya pintado de verdad. Un escenario que no
        // llega a pintar sus datos pasaria axe por no tener nada que revisar, y
        // seria el peor verde posible: el que dice que se reviso algo que no
        // estaba.
        for (const selector of escenario.exige) {
          await expect(
            pagina.locator(selector).first(),
            `${escenario.id}: el banco no pinto «${selector}». La prueba no esta ` +
              'auditando el estado poblado que dice auditar; probablemente el cuerpo ' +
              'de prueba dejo de encajar con el contrato.',
          ).toBeAttached({ timeout: 15_000 });
        }

        const resultado = await new AxeBuilder({ page: pagina }).withTags(NORMAS).analyze();
        const graves = resultado.violations.filter((v) => GRAVES.has(v.impact));

        expect(
          graves.map((v) => `${v.id} (${v.impact}) × ${v.nodes.length}: ${v.help}`),
          `Accesibilidad grave en ${escenario.id} a ${pantalla.nombre}, con datos`,
        ).toEqual([]);
      } finally {
        await contexto.close();
      }
    });
  }
}
