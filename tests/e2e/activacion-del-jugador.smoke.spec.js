/**
 * R18 - Un jugador nuevo entra al Nexo con una cuenta util.
 *
 * ## Que afirma
 *
 * Una cuenta creada desde cero, sin que nadie la prepare a mano ni toque una
 * base de datos, termina en un estado real y jugable: correo entregado,
 * creditos, heroe, inventario, tienda con catalogo, y la contrasena se puede
 * recuperar. Y al volver a entrar, todo sigue ahi.
 *
 * ## Por que existe
 *
 * Cada afirmacion corresponde a algo que estaba roto y que nadie veia:
 *
 *   - los correos salian a un buzon de pruebas que no reenvia a nadie;
 *   - una sola solicitud de "olvide mi contrasena" dejaba DOS correos;
 *   - las cuentas anteriores al alta automatica nunca recibieron creditos ni
 *     heroe, y seguian asi para siempre.
 *
 * Ninguno lo detecto una prueba unitaria, porque ninguno esta en una unidad:
 * estan en el camino completo. Por eso esta prueba recorre el camino.
 *
 * Es `serial` a proposito: cada paso usa la cuenta que creo el anterior, que
 * es lo que hace un jugador. El banco de `tests/e2e/` no levanta correo ni
 * Mailpit, asi que esto corre contra el entorno desplegado.
 */

import { test, expect, request as apiRequest } from '@playwright/test';

const AWS = process.env.E2E_AWS ?? 'http://35.168.124.119';
const CLAVE = 'Contrasena-R18-2026';
const CLAVE_NUEVA = 'Contrasena-R18-Nueva-2026';

/** Cuanto se espera a que el alta del jugador termine sus pasos remotos. */
const ESPERA_ALTA_MS = 45000;

function cuerpoDelToken(jwt) {
  const base64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(base64, 'base64').toString('utf8'));
}

function conToken(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

/**
 * Los correos que hay ahora mismo para una direccion.
 *
 * Que esta prueba tenga que mirar un buzon de pruebas ES el hallazgo:
 * mientras siga aqui, ningun correo llega a una bandeja de verdad. Cuando el
 * entorno apunte a un proveedor real, esto se sustituye por la evidencia de
 * entrega del propio servicio (`GET /api/v1/correos/envios`), que ya publica
 * el Message-ID con el que cruzarlo contra el proveedor.
 */
async function correosPara(api, direccion) {
  const bandeja = await api.get('/mailpit/api/v1/search', {
    params: { query: `to:${direccion}` },
  });
  if (!bandeja.ok()) {
    return null;
  }
  return (await bandeja.json()).messages ?? [];
}
test.describe('R18 - activacion del jugador nuevo', () => {
  test.describe.configure({ mode: 'serial' });

  const marca = Date.now();
  const apodo = `r18_${marca}`;
  const email = `r18.${marca}@nexus.test`;

  let api;
  let sesion;
  let uid;

  test.beforeAll(async () => {
    api = await apiRequest.newContext({ baseURL: AWS, ignoreHTTPSErrors: true });
  });

  test.afterAll(async () => {
    await api?.dispose();
  });

  // ------------------------------------------------------------------ alta

  test('el registro crea la cuenta activa y con rol de jugador', async () => {
    const registro = await api.post('/api/v1/auth/registro', {
      multipart: { nombres: 'Jugador', apellidos: 'Nuevo', email, password: CLAVE, apodo },
    });

    expect(registro.status(), await registro.text()).toBe(201);
    const cuenta = await registro.json();
    // RF-AUT-001: la cuenta nace ACTIVA. El correo no es una puerta: si el
    // servicio de correo falla, la cuenta se crea igual.
    expect(cuenta.estado).toBe('ACTIVO');
    expect(cuenta.rol.nombre).toBe('JUGADOR');
    expect(cuenta.publicId).toBeTruthy();
  });

  test('el correo de bienvenida sale del sistema', async () => {
    const mensajes = await correosPara(api, email);
    test.skip(mensajes === null, 'este entorno no expone el buzon de pruebas');

    await expect
      .poll(async () => (await correosPara(api, email)).length, {
        timeout: 20000,
        message: 'el correo de bienvenida no salio',
      })
      .toBeGreaterThan(0);
  });

  test('el login funciona y el token trae el identificador publico', async () => {
    const login = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });

    expect(login.status(), await login.text()).toBe(200);
    sesion = await login.json();
    const claims = cuerpoDelToken(sesion.token);
    uid = claims.uid;
    expect(uid, 'sin uid en el token, ningun servicio sabe de quien habla').toBeTruthy();
    expect(claims.rol).toBe('JUGADOR');
  });

  // ------------------------------------------------- la cuenta queda lista

  test('el alta del jugador termina sin que nadie la prepare a mano', async () => {
    await expect
      .poll(
        async () => {
          const estado = await api.get('/api/v1/auth/onboarding', {
            headers: conToken(sesion.token),
          });
          return estado.ok() ? (await estado.json()).estado : 'SIN_RESPUESTA';
        },
        { timeout: ESPERA_ALTA_MS, message: 'el alta del jugador no llego a COMPLETO' },
      )
      .toBe('COMPLETO');

    const estado = await (
      await api.get('/api/v1/auth/onboarding', { headers: conToken(sesion.token) })
    ).json();
    expect(estado.listo).toBe(true);
    expect(estado.pasos.map((p) => p.estado)).not.toContain('PENDIENTE');
  });

  test('empieza con creditos, y con UN solo movimiento inicial', async () => {
    const saldo = await api.get(`/api/v1/creditos/${uid}/saldo`, {
      headers: conToken(sesion.token),
    });
    expect(saldo.status(), await saldo.text()).toBe(200);
    const { saldoDisponible } = await saldo.json();
    // El importe lo decide un parametro, no esta prueba: se afirma que se
    // puede apostar algo, no cuanto.
    expect(Number(saldoDisponible)).toBeGreaterThan(0);

    const movimientos = await api.get(`/api/v1/creditos/${uid}/movimientos`, {
      headers: conToken(sesion.token),
    });
    expect(movimientos.status()).toBe(200);
    const pagina = await movimientos.json();
    // Exactamente uno: si el alta se reintenta, el bono NO se duplica --
    // ms-finanzas lo descarta por su refId.
    expect(pagina.content).toHaveLength(1);
    expect(pagina.content[0].concepto).toContain('bono-registro');
    expect(pagina.content[0].referenciaId).toContain(uid);
    expect(Number(pagina.content[0].monto)).toBe(Number(saldoDisponible));
  });

  test('empieza con un heroe de verdad en el inventario', async () => {
    const inventario = await api.get('/api/v1/inventario/elementos', {
      headers: conToken(sesion.token),
    });

    expect(inventario.status(), await inventario.text()).toBe(200);
    const { elementos } = await inventario.json();
    const heroes = elementos.filter((e) => e.tipo === 'HEROE');
    expect(heroes.length, 'sin heroe no se puede entrar a una batalla').toBeGreaterThan(0);
    expect(heroes[0].nombrePropio).toBeTruthy();
    expect(heroes[0].productoId, 'el heroe sale del catalogo, no inventado').toBeTruthy();
  });

  test('reintentar el alta no duplica nada', async () => {
    const reintento = await api.post('/api/v1/auth/onboarding/reintentos', {
      headers: conToken(sesion.token),
    });
    expect([200, 202, 409]).toContain(reintento.status());

    const movimientos = await (
      await api.get(`/api/v1/creditos/${uid}/movimientos`, { headers: conToken(sesion.token) })
    ).json();
    expect(movimientos.content, 'el bono no puede pagarse dos veces').toHaveLength(1);
  });
  // ------------------------------------------------------ hay donde gastar

  test('la tienda tiene catalogo, o dice por que no', async () => {
    const productos = await api.get('/api/v1/productos', { headers: conToken(sesion.token) });

    if ([502, 503].includes(productos.status())) {
      test.info().annotations.push({
        type: 'servicio degradado',
        description: `el catalogo no responde (HTTP ${productos.status()})`,
      });
      test.skip(true, 'el servicio del catalogo no esta atendiendo en este entorno');
    }
    expect(productos.status()).toBe(200);
    const pagina = await productos.json();
    const filas = pagina.content ?? pagina;
    expect(filas.length, 'una tienda vacia no deja empezar a nadie').toBeGreaterThan(0);
    // Precio de verdad: un 0 por defecto tecnico es peor que no tener tienda.
    expect(filas.some((p) => Number(p.precioCreditos) > 0)).toBe(true);
  });

  test('la vitrina de subastas responde o se degrada, pero no revienta', async () => {
    const subastas = await api.get('/api/v1/subastas', { headers: conToken(sesion.token) });

    // 200 con lista (aunque este vacia) o una caida declarada. Lo que no vale
    // es un 500: eso seria un fallo del servicio, no una ausencia de datos.
    expect([200, 502, 503]).toContain(subastas.status());
    if (subastas.status() === 200) {
      const cuerpo = await subastas.json();
      expect(cuerpo.contenido ?? cuerpo.content ?? cuerpo).toBeDefined();
    }
  });

  // ----------------------------------------------- todo sigue ahi al volver

  test('salir y volver a entrar conserva creditos, heroe y alta', async () => {
    const salida = await api.post('/api/v1/auth/logout', { headers: conToken(sesion.token) });
    expect([200, 204]).toContain(salida.status());

    const vuelta = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
    expect(vuelta.status()).toBe(200);
    const nueva = await vuelta.json();

    const saldo = await (
      await api.get(`/api/v1/creditos/${uid}/saldo`, { headers: conToken(nueva.token) })
    ).json();
    expect(Number(saldo.saldoDisponible)).toBeGreaterThan(0);

    const inventario = await (
      await api.get('/api/v1/inventario/elementos', { headers: conToken(nueva.token) })
    ).json();
    expect(inventario.elementos.filter((e) => e.tipo === 'HEROE').length).toBeGreaterThan(0);

    const alta = await (
      await api.get('/api/v1/auth/onboarding', { headers: conToken(nueva.token) })
    ).json();
    expect(alta.estado).toBe('COMPLETO');
    sesion = nueva;
  });

  // ------------------------------------------------ recuperar la contrasena

  test('pedir el restablecimiento no revela si la cuenta existe', async () => {
    const existe = await api.post('/api/v1/auth/restablecer/solicitar', { data: { email } });
    const noExiste = await api.post('/api/v1/auth/restablecer/solicitar', {
      data: { email: `no.existe.${marca}@nexus.test` },
    });

    expect(existe.status()).toBe(200);
    expect(noExiste.status()).toBe(200);
    // Misma respuesta palabra por palabra: si difirieran, cualquiera podria
    // averiguar que correos estan registrados.
    expect(await noExiste.text()).toBe(await existe.text());
  });

  test('el codigo llega UNA vez, no dos', async () => {
    const mensajes = await correosPara(api, email);
    test.skip(mensajes === null, 'este entorno no expone el buzon de pruebas');

    const deRecuperacion = async () =>
      (await correosPara(api, email)).filter((m) => m.Subject.includes('Recupera'));

    await expect
      .poll(async () => (await deRecuperacion()).length, {
        timeout: 20000,
        message: 'el correo de recuperacion no salio',
      })
      .toBeGreaterThan(0);

    // Una solicitud, un correo. Hasta R18.3 salian dos identicos: la entrega
    // ocurria dentro de la peticion y el cliente reintentaba al agotar su
    // espera de dos segundos.
    expect(await deRecuperacion(), 'una solicitud deja un solo correo').toHaveLength(1);
  });

  test('el codigo cambia la contrasena, y solo sirve una vez', async () => {
    const mensajes = await correosPara(api, email);
    test.skip(mensajes === null, 'este entorno no expone el buzon de pruebas');

    const correo = mensajes.find((m) => m.Subject.includes('Recupera'));
    const detalle = await (await api.get(`/mailpit/api/v1/message/${correo.ID}`)).json();
    const codigo = /letter-spacing:8px;[^>]*>([A-Z0-9]{6,12})</.exec(detalle.HTML)?.[1];
    expect(codigo, 'el correo tiene que traer un codigo legible').toBeTruthy();

    const cambio = await api.post('/api/v1/auth/restablecer/confirmar', {
      data: { token: codigo, nuevaPassword: CLAVE_NUEVA },
    });
    expect(cambio.status(), await cambio.text()).toBe(200);

    const conLaNueva = await api.post('/api/v1/auth/login', {
      data: { email, password: CLAVE_NUEVA },
    });
    expect(conLaNueva.status()).toBe(200);

    const conLaVieja = await api.post('/api/v1/auth/login', { data: { email, password: CLAVE } });
    expect(conLaVieja.status(), 'la contrasena anterior deja de valer').not.toBe(200);

    // Un codigo de un solo uso que se puede reutilizar no es de un solo uso.
    const reintento = await api.post('/api/v1/auth/restablecer/confirmar', {
      data: { token: codigo, nuevaPassword: 'Otra-Contrasena-R18-2026' },
    });
    expect(reintento.status()).not.toBe(200);
  });

  test('despues de cambiar la contrasena la cuenta sigue siendo la misma', async () => {
    const login = await api.post('/api/v1/auth/login', {
      data: { email, password: CLAVE_NUEVA },
    });
    const nueva = await login.json();
    expect(cuerpoDelToken(nueva.token).uid).toBe(uid);

    const saldo = await (
      await api.get(`/api/v1/creditos/${uid}/saldo`, { headers: conToken(nueva.token) })
    ).json();
    expect(Number(saldo.saldoDisponible)).toBeGreaterThan(0);
  });
});
