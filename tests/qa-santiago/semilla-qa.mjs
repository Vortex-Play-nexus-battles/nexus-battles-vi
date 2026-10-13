// Semilla de datos de la campana QA. Se ejecuta una vez sobre el banco ya sembrado
// por tests/e2e/sembrar.sh y deja los identificadores en datos-qa.json.
//
//   node tests/qa-santiago/semilla-qa.mjs
import { request } from '@playwright/test';
import fs from 'node:fs';
import { execSync } from 'node:child_process';
import { sesionDe } from '../e2e/ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const FINANZAS = 'http://localhost:8093/api/v1';
const SALIDA = new URL('./datos-qa.json', import.meta.url);
const api = await request.newContext({ baseURL: BORDE });
const sufijo = Date.now().toString(36).slice(-4);
const IMG = (n) => `/frontend/app-web/src/cuentas/avatares/${n}.jpg`;
const IMAGENES = ['guerrero-berserker', 'mago-de-fuego', 'mago-de-hielo', 'arquero-cazador', 'picaro-asesino', 'chaman-sanador', 'gran-mago-sabio', 'comandante-con-casco', 'alquimista-picaro-veneno'];

const json = (token) => ({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', Accept: 'application/json, application/problem+json' });
async function post(url, token, data, extra = {}) {
  const r = await api.post(url, { headers: { ...json(token), ...extra }, data, failOnStatusCode: false });
  const texto = await r.text();
  if (r.status() >= 300) throw new Error(`${url} -> ${r.status()} ${texto}`);
  return texto ? JSON.parse(texto) : null;
}

const s = {};
for (const apodo of ['admin_e2e', 'vendedor1_qa', 'vendedor2_qa', 'comprador_qa', 'comprador2_qa', 'cofres_qa']) {
  s[apodo] = await sesionDe(api, apodo);
  console.log('sesion', apodo, s[apodo].claims.rol);
}
const banco = await api.post('/api/v1/auth/token', {
  headers: { Authorization: `Basic ${Buffer.from('e2e-banco:e2e-secreto-del-banco-de-pruebas').toString('base64')}`, 'Content-Type': 'application/x-www-form-urlencoded' },
  data: 'grant_type=client_credentials',
});
const tokenBanco = (await banco.json()).access_token;

// ---------- catalogo de la tienda (dinero real, COP) ----------
const nombresArmas = ['Espada del Alba', 'Hacha de Obsidiana', 'Arco del Viento', 'Lanza Rúnica', 'Martillo del Trueno', 'Daga Sombría', 'Báculo de Ámbar', 'Mandoble de Hierro', 'Ballesta del Norte', 'Cimitarra Solar'];
const nombresArmaduras = ['Yelmo del Centinela', 'Peto de Escamas', 'Guanteletes de Bronce', 'Grebas del Errante', 'Escudo del Bastión', 'Capa de la Bruma', 'Brazales Arcanos', 'Coraza Dracónica', 'Botas del Rastreador', 'Hombreras del Coloso'];
const partes = ['CASCO', 'PECHO', 'GUANTES', 'PANTALON', 'PECHO', 'PECHO', 'BRAZALETES', 'PECHO', 'ZAPATOS', 'PECHO'];
const tienda = [];
const ahora = Date.now();
for (let i = 0; i < 10; i += 1) {
  const promo = i === 0 ? { porcentaje: 25, desde: new Date(ahora - 3600e3).toISOString(), hasta: new Date(ahora + 7 * 864e5).toISOString() }
    : i === 1 ? { porcentaje: 40, desde: new Date(ahora - 3600e3).toISOString(), hasta: new Date(ahora + 4 * 60e3).toISOString() }
    : undefined;
  tienda.push({ nombre: nombresArmas[i], imagen: IMG(IMAGENES[i % IMAGENES.length]), descripcion: `Arma forjada en el Nexo. Golpe +${10 + i}, ideal para combate cuerpo a cuerpo.`, tipo: 'ARMA', tiraje: -1, premium: false, precioCreditos: 120 + i * 15, precioMonedaReal: 15000 + i * 3500, poderDeAtaque: 10 + i, tasaDeCaida: 30, ...(promo ? { promocion: promo } : {}) });
  tienda.push({ nombre: nombresArmaduras[i], imagen: IMG(IMAGENES[(i + 4) % IMAGENES.length]), descripcion: `Armadura del Nexo. Defensa +${5 + i}; resiste ataques de fuego y hielo.`, tipo: 'ARMADURA', tiraje: -1, premium: false, precioCreditos: 150 + i * 15, precioMonedaReal: 22000 + i * 4200, defensa: 5 + i, parte: partes[i], tasaDeCaida: 30 });
}
const productosTienda = [];
for (const p of tienda) {
  try {
    const creado = await post('/api/v1/productos', s.admin_e2e.token, p);
    productosTienda.push({ id: creado.id, nombre: p.nombre, tipo: p.tipo, precio: p.precioMonedaReal, promocion: p.promocion ?? null });
  } catch (e) {
    console.log('producto rechazado', p.nombre, e.message.slice(0, 300));
  }
}
console.log('productos de tienda', productosTienda.length);
if (process.env.SOLO_TIENDA) {
  const previos = JSON.parse(fs.readFileSync(SALIDA, 'utf8'));
  fs.writeFileSync(SALIDA, JSON.stringify({ ...previos, productosTienda }, null, 2));
  console.log('datos-qa.json actualizado (solo tienda)');
  await api.dispose();
  process.exit(0);
}

// ---------- objetos para subastar (creditos) ----------
const subastables = [];
const nombresSub = ['Garra del Fénix', 'Filo de Escarcha', 'Coraza de Runas', 'Espada Legendaria del Rey', 'Arco Élfico', 'Peto del Berserker', 'Mazo Rúnico', 'Sable Dorado', 'Cota del Asesino', 'Bastón del Sabio'];
for (let i = 0; i < nombresSub.length; i += 1) {
  const p = { nombre: nombresSub[i], imagen: IMG(IMAGENES[(i + 2) % IMAGENES.length]), descripcion: `Reliquia subastable: ${nombresSub[i].toLowerCase()} con habilidad «Golpe ${i % 2 ? 'gélido' : 'ígneo'}».`, tipo: i % 3 === 2 ? 'ARMADURA' : 'ARMA', tiraje: -1, premium: false, precioCreditos: 60 + i * 10, tasaDeCaida: 20, ...(i % 3 === 2 ? { defensa: 6 + i, parte: 'PECHO' } : { poderDeAtaque: 8 + i }) };
  const creado = await post('/api/v1/productos', s.admin_e2e.token, p);
  subastables.push({ id: creado.id, nombre: p.nombre, tipo: p.tipo });
}

// ---------- entregas al inventario ----------
async function entregar(apodo, productoId, ref) {
  const r = await post('/api/v1/inventario/entregas', tokenBanco, { uid: s[apodo].claims.uid, origen: 'ADMINISTRACION', referencia: ref, productos: [{ productoId, cantidad: 1 }] }, { 'Idempotency-Key': ref });
  return r.elementos[0].id;
}
async function acreditar(apodo, monto) {
  const r = await api.post(`${FINANZAS}/creditos/acreditar`, { headers: json(tokenBanco), data: { uid: s[apodo].claims.uid, monto, refId: `qa-${apodo}-${sufijo}`, concepto: 'semilla-qa' }, failOnStatusCode: false });
  console.log('acreditar', apodo, monto, r.status());
}
await acreditar('vendedor1_qa', 200);
await acreditar('vendedor2_qa', 200);
await acreditar('comprador_qa', 3000);
await acreditar('comprador2_qa', 3000);

const elementos = { vendedor1_qa: [], vendedor2_qa: [] };
for (let i = 0; i < 18; i += 1) {
  const vendedor = i < 10 ? 'vendedor1_qa' : 'vendedor2_qa';
  const prod = subastables[i % subastables.length];
  const el = await entregar(vendedor, prod.id, `qa-entrega-${vendedor}-${i}-${sufijo}`);
  elementos[vendedor].push({ elementoId: el, productoId: prod.id, nombre: prod.nombre });
}
// objetos extra sin publicar para las pruebas de publicacion/puja
const extra = { vendedor2_qa: [] };
for (let i = 0; i < 4; i += 1) {
  const prod = subastables[(i + 3) % subastables.length];
  extra.vendedor2_qa.push({ elementoId: await entregar('vendedor2_qa', prod.id, `qa-extra-${i}-${sufijo}`), productoId: prod.id, nombre: prod.nombre });
}

// ---------- subastas activas ----------
const subastas = [];
for (const vendedor of ['vendedor1_qa', 'vendedor2_qa']) {
  for (const [i, el] of elementos[vendedor].entries()) {
    if (vendedor === 'vendedor2_qa' && i >= 8) break;
    const precio = 20 + ((i * 37) % 180);
    const datos = { elementoInventarioId: el.elementoId, productoId: el.productoId, duracion: i % 2 ? '48H' : '24H', precioInicial: precio, ...(i % 3 === 0 ? { precioCompraInmediata: precio * 3 } : {}) };
    try {
      const sub = await post('/api/v1/subastas', s[vendedor].token, datos, { 'Idempotency-Key': `qa-pub-${vendedor}-${i}-${sufijo}` });
      subastas.push({ id: sub.id, vendedor, nombre: el.nombre, precio, compraInmediata: datos.precioCompraInmediata ?? null, duracion: datos.duracion });
    } catch (e) {
      console.log('publicar fallo', vendedor, i, e.message.slice(0, 200));
    }
  }
}
console.log('subastas activas', subastas.length);
// pujas para tener contadores distintos de cero
for (const [i, sub] of subastas.slice(0, 6).entries()) {
  for (let k = 0; k <= i % 3; k += 1) {
    const comprador = k % 2 ? 'comprador2_qa' : 'comprador_qa';
    const monto = sub.precio + 5 * (k + 1) + k * 10;
    await api.post(`/api/v1/subastas/${sub.id}/pujas`, { headers: { ...json(s[comprador].token), 'Idempotency-Key': `qa-puja-${sub.id}-${k}` }, data: { monto }, failOnStatusCode: false });
    await new Promise((r) => setTimeout(r, 5200));
  }
}
// Precondicion: una publicacion marcada como del Maestro de Juego (el rol aun no existe en identidad).
const gm = subastas[subastas.length - 1];
execSync(`docker compose -p nexus-e2e exec -T e2e-subastas-db psql -U subastas_user -d subastas_db -c "UPDATE subastas SET es_maestro_de_juego=true WHERE id='${gm.id}'"`, { stdio: 'inherit' });

fs.writeFileSync(SALIDA, JSON.stringify({ sufijo, productosTienda, subastables, elementos, extra, subastas, maestroDeJuego: gm.id, uids: Object.fromEntries(Object.entries(s).map(([k, v]) => [k, v.claims.uid])) }, null, 2));
console.log('datos-qa.json listo');
await api.dispose();
