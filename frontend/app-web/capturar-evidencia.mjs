/**
 * Genera las capturas de evidencia del Sprint 1 para HU-INV-001 y HU-INV-004.
 * Se regeneran con: node capturar-evidencia.mjs
 */
import { chromium } from '@playwright/test';

const PUERTO = 4399;
const BASE = `http://127.0.0.1:${PUERTO}/contenido/inventario/index.html?propietario=demo`;
const SALIDA = 'src/contenido/inventario/evidencia';

const tipos = ['HEROE', 'ARMA', 'ARMADURA', 'ITEM', 'EPICA', 'HABILIDAD'];
const nombres = ['Espada del alba', 'Yelmo runico', 'Pocion mayor', 'Hacha de Vorn',
  'Grebas de hierro', 'Anillo del vacio', 'Arco largo', 'Capa de sombras',
  'Maza estelar', 'Guantes de cuero', 'Elixir de furia', 'Escudo torre',
  'Daga curva', 'Casco alado', 'Amuleto palido', 'Lanza de obsidiana'];

function pagina(total) {
  const elementos = Array.from({ length: Math.min(16, total) }, (_, i) => ({
    id: `e${i}`, productoId: `p${i}`, tipo: tipos[i % tipos.length], nombrePropio: nombres[i],
  }));
  return { elementos, numero: 0, tamanio: 16, totalElementos: total,
    totalPaginas: Math.ceil(total / 16), ultima: total <= 16 };
}

// Requiere un servidor estatico sirviendo `src` en el puerto de arriba, p.ej.:
//   python -m http.server 4399 --directory src --bind 127.0.0.1
const navegador = await chromium.launch();

async function capturar(nombre, { ancho, alto, total }) {
  const ctx = await navegador.newContext({ viewport: { width: ancho, height: alto } });
  const page = await ctx.newPage();
  await page.route('**/api/v1/inventarios/**', (r) => r.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify(pagina(total)),
  }));
  await page.goto(BASE);
  await page.waitForFunction(() => !document.querySelector('.estado-carga'));
  await page.screenshot({ path: `${SALIDA}/${nombre}.png` });
  await ctx.close();
  console.log(`  ${nombre}.png  (${ancho}x${alto})`);
}

console.log('Capturando evidencia:');
await capturar('vitrina-1360x768', { ancho: 1360, alto: 768, total: 40 });
await capturar('vitrina-1024x768', { ancho: 1024, alto: 768, total: 40 });
await capturar('vitrina-768x1024', { ancho: 768, alto: 1024, total: 40 });
await capturar('vitrina-375x812', { ancho: 375, alto: 812, total: 40 });
await capturar('estado-vacio-1360x768', { ancho: 1360, alto: 768, total: 0 });

// Panel de Mi Cuenta para un visitante (HU-INV-004, criterio 3).
const ctx = await navegador.newContext({ viewport: { width: 1360, height: 768 } });
const page = await ctx.newPage();
await page.route('**/api/v1/inventarios/**', (r) => r.fulfill({
  status: 200, contentType: 'application/json', body: JSON.stringify(pagina(40)),
}));
await page.goto(BASE);
await page.waitForFunction(() => !document.querySelector('.estado-carga'));
await page.locator('.barra__acceso', { hasText: 'Mi Cuenta' }).click();
await page.screenshot({ path: `${SALIDA}/barra-visitante-mi-cuenta.png` });
console.log('  barra-visitante-mi-cuenta.png  (1360x768)');
await ctx.close();

await navegador.close();
process.exit(0);
