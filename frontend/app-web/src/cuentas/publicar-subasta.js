import { montarCabecera, leerSesion } from '../comun/cabecera-app.js';
import { cuerpoDelToken } from '../comun/identidad.js';
import { baseDeApi, rutaDeApi } from '../comun/base-api.js';
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { consultarPagina } from '../contenido/inventario/cliente-inventario.js';
import { consultarProducto as leerDelCatalogo } from '../contenido/inventario/cliente-productos.js';
import { NOMBRE_DEL_TIPO, nombreDelTipo } from '../comun/ui/formato.js';
import {
  publicarSubasta,
  crearClavePublicacion,
  ErrorPublicacion,
} from './cliente-publicacion-subastas.js';
import { SIN_NOMBRE, crearResolutorDeNombres, nombreLegible } from './nombre-de-producto.js';

/**
 * Respaldo de la Tabla 25 por si las reglas del servidor no llegan. Desde B8
 * la pantalla pide GET /subastas/reglas al montarse y usa lo que diga el
 * servidor (duraciones, comisiones y si el incremento minimo esta
 * configurado); estas cifras solo evitan una pantalla en blanco sin red.
 */
const DURACIONES = { '24H': { horas: 24, comision: 1 }, '48H': { horas: 48, comision: 3 } };

/**
 * D-43 — el incremento mínimo es una decisión tomada (5 créditos en
 * admin-parametros). Si un entorno lo tuviera vacío, se dice qué falta sin
 * presentarlo como una decisión pendiente.
 */
const SIN_INCREMENTO =
  'El incremento mínimo entre pujas no está configurado en administración: no se pueden publicar subastas hasta que un administrador lo fije.';

/** «1 crédito», «5 créditos». */
function textoDeCreditos(cantidad) {
  return `${cantidad} ${cantidad === 1 ? 'crédito' : 'créditos'}`;
}

/**
 * GET /subastas/reglas (ms-subastas-listado.yaml 1.1.0). Publica. Null si no
 * responde: la pantalla sigue con el respaldo y el servidor decide al publicar.
 */
export async function consultarReglasDeSubastas(fetchImpl = globalThis.fetch) {
  if (typeof fetchImpl !== 'function') {
    return null;
  }
  try {
    const respuesta = await fetchImpl(rutaDeApi('/subastas/reglas'), {
      headers: { Accept: 'application/json' },
    });
    return respuesta?.ok ? await respuesta.json() : null;
  } catch {
    return null;
  }
}
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function sesionUtilizable() {
  const sesion = leerSesion();
  const claims = cuerpoDelToken(sesion.token);
  // Publicación exige uid; el respaldo histórico de usuarioId no sustituye ese claim.
  return sesion.autenticado && UUID.test(claims?.uid ?? '') ? { ...sesion, uid: claims.uid } : null;
}

export function validarCondiciones(elemento, duracion, inicial, inmediata) {
  const errores = {};
  if (!elemento?.id || !UUID.test(elemento.productoId ?? '') || elemento.disponible === false) {
    errores.producto = 'Selecciona un producto disponible de tu inventario.';
  }
  if (!Object.hasOwn(DURACIONES, duracion)) {
    errores.duracion = 'Elige una duración de 24 o 48 horas.';
  }
  const precio = Number(inicial);
  if (!String(inicial).trim() || !Number.isFinite(precio) || precio <= 0) {
    errores.inicial = 'Introduce un precio inicial válido y mayor que cero.';
  }
  if (String(inmediata).trim()) {
    const compra = Number(inmediata);
    // B8 — 7.7.2: superior al precio minimo, no igual (el servidor tambien lo exige).
    if (!Number.isFinite(compra) || compra <= 0 || compra <= precio) {
      errores.inmediata =
        'La compra inmediata debe ser superior al precio inicial (mínimo de puja).';
    }
  }
  return errores;
}

/** Adaptación de transporte: conserva el cliente de inventario y su contrato. */
async function transporteInventario(ruta, opciones) {
  const respuesta = await fetchWithHttpErrorInterceptor(`${baseDeApi()}${ruta}`, opciones);
  if (respuesta.status === 401) {
    throw new ErrorPublicacion('Tu sesión no es válida. Inicia sesión para continuar.', {
      status: 401,
    });
  }
  return respuesta;
}

export async function montarPublicacion(
  raiz,
  {
    consultar = consultarPagina,
    publicar = publicarSubasta,
    crearClave = crearClavePublicacion,
    consultarReglas = consultarReglasDeSubastas,
    consultarProducto = (id) => leerDelCatalogo(id, { fetchImpl: transporteInventario }),
  } = {},
) {
  const cabecera = document.querySelector('[data-cabecera-app]');
  if (cabecera) {
    montarCabecera(cabecera, { vista: 'publicar-subasta', seccionActiva: 'subasta' });
  }
  // Solo marcado estático. Nombres de inventario y respuestas se asignan con textContent.
  raiz.innerHTML = `
    <a href="./subastas.html">← Volver a Subastas</a>
    <header class="publicacion__intro">
      <p class="publicacion__antetitulo">MERCADO · NUEVA PUBLICACIÓN</p>
      <h1>Publicar un producto en subasta</h1>
      <p>Elige tu producto, define las condiciones y confirma la comisión.</p>
    </header>
    <p id="nexus-rbac-forbidden" class="publicacion__estado" role="status" aria-live="polite" aria-atomic="true" hidden></p>
    <p data-sesion hidden><a href="./login.html">Iniciar sesión</a></p>
    <form novalidate hidden>
      <div class="publicacion__rejilla">
        <div class="pila">
          <section class="publicacion__panel" aria-labelledby="titulo-producto">
            <h2 id="titulo-producto"><span class="publicacion__paso">01</span> Producto</h2>
            <fieldset data-productos>
              <div class="campo">
                <label for="producto">Producto de tu inventario</label>
                <select id="producto" class="campo__control" required aria-describedby="error-producto"><option value="">Selecciona un producto</option></select>
                <small id="error-producto" class="campo__error"></small>
              </div>
              <p data-inventario role="status" aria-live="polite"></p>
              <p data-sin-inventario class="publicacion__ayuda" hidden><a href="./tienda.html">Ir a la tienda</a></p>
              <nav class="publicacion__paginacion" aria-label="Páginas del inventario">
                <button type="button" data-anterior class="boton boton--secundario">Anterior</button>
                <span data-pagina></span>
                <button type="button" data-siguiente class="boton boton--secundario">Siguiente</button>
                <button type="button" data-recargar class="boton boton--secundario" hidden>Reintentar carga</button>
              </nav>
            </fieldset>
            <div data-aviso-catalogo class="publicacion__aviso-catalogo" hidden>
              <p role="status">No pudimos consultar el catálogo de productos: mientras tanto ves el nombre que el objeto tiene en tu inventario.</p>
              <button type="button" data-reintentar-catalogo class="boton boton--secundario boton--pequeno">Reintentar</button>
            </div>
            <p class="publicacion__ayuda">Los productos no disponibles aparecen deshabilitados. Al publicar se comprobarán también el uso, la propiedad y si el producto es subastable.</p>
            <p class="publicacion__ayuda" data-incremento-minimo hidden></p>
          </section>
          <section class="publicacion__panel" aria-labelledby="titulo-condiciones">
            <h2 id="titulo-condiciones"><span class="publicacion__paso">02</span> Condiciones de publicación</h2>
            <fieldset data-condiciones class="pila">
              <fieldset aria-describedby="error-duracion">
                <legend>Duración y comisión</legend>
                <div class="publicacion__duraciones">
                  <label class="publicacion__duracion"><input type="radio" name="duracion" value="24H" checked required /><span>24 horas<small data-comision="24H">Comisión: 1 crédito</small></span></label>
                  <label class="publicacion__duracion"><input type="radio" name="duracion" value="48H" required /><span>48 horas<small data-comision="48H">Comisión: 3 créditos</small></span></label>
                </div>
                <small id="error-duracion" class="campo__error"></small>
              </fieldset>
              <div class="campo"><label for="inicial">Precio inicial / mínimo (créditos)</label><input id="inicial" class="campo__control" type="number" step="any" min="0" required aria-describedby="error-inicial" /><small id="error-inicial" class="campo__error"></small></div>
              <div class="campo"><label for="inmediata">Compra inmediata (créditos, opcional)</label><input id="inmediata" class="campo__control" type="number" step="any" min="0" aria-describedby="ayuda-inmediata error-inmediata" /><small id="ayuda-inmediata" class="publicacion__ayuda">Déjalo vacío si solo deseas recibir pujas.</small><small id="error-inmediata" class="campo__error"></small></div>
            </fieldset>
          </section>
        </div>
        <section class="publicacion__panel publicacion__resumen" aria-labelledby="titulo-confirmacion">
          <h2 id="titulo-confirmacion"><span class="publicacion__paso">03</span> Confirmación</h2>
          <dl aria-live="polite" aria-atomic="true">
            <dt>Producto</dt><dd data-resumen-producto>Sin seleccionar</dd>
            <dt>Duración</dt><dd data-resumen-duracion></dd>
            <dt>Comisión de publicación</dt><dd data-resumen-comision></dd>
            <dt>Precio inicial</dt><dd data-resumen-inicial></dd>
            <dt>Compra inmediata</dt><dd data-resumen-inmediata></dd>
          </dl>
          <p class="publicacion__ayuda">La comisión indicada se cobra al publicar la subasta.</p>
          <label class="publicacion__aceptacion"><input type="checkbox" id="aceptar" /><span>He revisado el producto y los precios y confirmo el cargo de la comisión indicada.</span></label>
          <button type="submit" class="boton boton--acento boton--grande" disabled>Confirmar y publicar</button>
          <p data-referencia class="publicacion__ayuda" hidden></p>
        </section>
      </div>
    </form>`;

  const $ = (selector) => raiz.querySelector(selector);
  const mensaje = $('#nexus-rbac-forbidden');
  const form = $('form');
  const producto = $('#producto');
  const inicial = $('#inicial');
  const inmediata = $('#inmediata');
  const aceptar = $('#aceptar');
  const enviar = $('[type="submit"]');
  const sesion = sesionUtilizable();
  let elementos = [];
  let pagina = 0;
  let totalPaginas = 0;
  let cargando = false;
  let enviando = false;
  let terminado = false;
  let intento = null;
  let retenido = false;
  let sinSesion = !sesion;
  // B8 — con el incremento minimo sin configurar el servidor no publica
  // (503 INCREMENTO_MINIMO_NO_CONFIGURADO): se dice antes y no se deja enviar.
  let sinIncremento = false;
  const duraciones = Object.fromEntries(
    Object.entries(DURACIONES).map(([codigo, valor]) => [codigo, { ...valor }]),
  );
  const almacenamiento = globalThis.sessionStorage;
  const claveAlmacen = sesion ? `nexus.hu-sub-001.intento:${sesion.uid}` : null;
  // PLAYER-07b (punto 27) — el nombre que se lee sale del catálogo; los ids
  // siguen viajando en la solicitud, pero ya no se pintan en ningún sitio.
  const nombres = crearResolutorDeNombres({ consultar: consultarProducto });

  /** Las etiquetas de la página de inventario que se ve, por id de elemento. */
  function etiquetas() {
    return etiquetasDelInventario(elementos, (productoId) => nombres.leer(productoId));
  }

  /** Pone al día el texto de las opciones ya pintadas (p. ej. llegó un nombre). */
  function etiquetarOpciones() {
    const porId = etiquetas();
    for (const opcion of producto.options) {
      const elemento = elementos.find((e) => e.id === opcion.value);
      if (elemento) {
        opcion.textContent = textoDeOpcion(porId.get(elemento.id), elemento);
      }
    }
  }

  /**
   * Pide al catálogo los productos de la página. Se espera un tope corto: un
   * catálogo lento no puede dejar la pantalla sin inventario. Lo que llegue
   * después re-etiqueta las opciones solo. Devuelve null (y no se espera
   * nada) si ya se conocen todos.
   */
  function nombrar(lista) {
    const ids = [...new Set(lista.map((e) => e.productoId).filter(Boolean))];
    if (ids.every((id) => (nombres.leer(id)?.estado ?? 'pendiente') !== 'pendiente')) {
      return null;
    }
    const todos = Promise.all(ids.map((id) => nombres.resolver(id)));
    todos.then(() => {
      if (lista === elementos) {
        etiquetarOpciones();
        actualizar();
      }
    });
    let tope;
    return Promise.race([
      todos,
      new Promise((resolver) => {
        tope = setTimeout(resolver, ESPERA_DEL_CATALOGO_MS);
      }),
    ]).finally(() => clearTimeout(tope));
  }

  function avisar(texto, error = false) {
    mensaje.textContent = texto;
    mensaje.hidden = !texto;
    mensaje.dataset.error = String(error);
  }
  function pedirSesion() {
    sinSesion = true;
    avisar('Debes iniciar sesión con una sesión válida para publicar una subasta.', true);
    $('[data-sesion]').hidden = false;
    const login = new URL('./login.html', globalThis.location.href);
    login.searchParams.set(
      'volver',
      `${globalThis.location.pathname}${globalThis.location.search}`,
    );
    $('[data-sesion] a').href = login.href;
    actualizar();
  }
  function datos() {
    const seleccionado = elementos.find((elemento) => elemento.id === producto.value);
    const duracion = $('[name="duracion"]:checked')?.value ?? '';
    const errores = validarCondiciones(seleccionado, duracion, inicial.value, inmediata.value);
    // Un number con entrada incompleta (p. ej. "1e") puede tener value vacío.
    if (inicial.validity.badInput) {
      errores.inicial = 'Introduce un precio inicial válido y mayor que cero.';
    }
    if (inmediata.validity.badInput) {
      errores.inmediata = 'Introduce una compra inmediata válida o deja el campo vacío.';
    }
    return { seleccionado, duracion, errores };
  }
  /** Se pone al primer envio: hasta entonces no se pinta ningun error de campo. */
  let intentado = false;

  function actualizar() {
    const { seleccionado, duracion, errores } = datos();
    const solicitud = retenido ? intento.solicitud : null;
    const configuracion = duraciones[solicitud?.duracion ?? duracion];
    // PLAYER-07b — antes «Espada de luz · ARMA · 7c9e…»: la constante del tipo
    // y el id interno del elemento. Ahora lo mismo que dice la opción elegida.
    const etiqueta = seleccionado ? etiquetas().get(seleccionado.id) : null;
    const resumenProducto = $('[data-resumen-producto]');
    if (retenido) {
      const guardado = nombreDelIntento(intento, nombres.leer(solicitud?.productoId));
      resumenProducto.textContent = guardado.texto;
      resumenProducto.dataset.origenNombre = guardado.origen;
    } else {
      resumenProducto.textContent = etiqueta?.texto ?? 'Sin seleccionar';
      resumenProducto.dataset.origenNombre = etiqueta?.origen ?? 'ninguno';
    }
    // Si el catálogo no respondió para algo de lo que se ve, se dice una vez,
    // con la salida al lado; el nombre de respaldo nunca es un código.
    const enPantalla = retenido ? [solicitud?.productoId] : elementos.map((e) => e.productoId);
    $('[data-aviso-catalogo]').hidden = !enPantalla.some(
      (id) => nombres.leer(id)?.estado === 'error',
    );
    $('[data-resumen-duracion]').textContent = configuracion
      ? `${configuracion.horas} horas`
      : 'Elige una duración';
    $('[data-resumen-comision]').textContent = configuracion
      ? `${configuracion.comision} ${configuracion.comision === 1 ? 'crédito' : 'créditos'}`
      : '—';
    let precioInicial = errores.inicial ? 'Pendiente' : `${Number(inicial.value)} créditos`;
    let precioCompra = 'Sin compra inmediata';
    if (inmediata.value) {
      precioCompra = errores.inmediata ? 'Revisa el precio' : `${Number(inmediata.value)} créditos`;
    }
    if (solicitud) {
      precioInicial = `${solicitud.precioInicial} créditos`;
      precioCompra =
        solicitud.precioCompraInmediata === null
          ? 'Sin compra inmediata'
          : `${solicitud.precioCompraInmediata} créditos`;
    }
    $('[data-resumen-inicial]').textContent = precioInicial;
    $('[data-resumen-inmediata]').textContent = precioCompra;
    for (const campo of ['producto', 'duracion', 'inicial', 'inmediata']) {
      // UX-R3.11 — «Selecciona un producto disponible de tu inventario» salia
      // en rojo nada mas abrir la pantalla, antes de que nadie tocara nada: un
      // error de validacion sobre un formulario intacto. Los errores aparecen
      // cuando ya se ha intentado enviar.
      $(`#error-${campo}`).textContent = retenido || !intentado ? '' : (errores[campo] ?? '');
      if (campo !== 'duracion') {
        $(`#${campo}`).setAttribute('aria-invalid', String(!retenido && Boolean(errores[campo])));
      }
    }
    $('[data-productos]').disabled = cargando || retenido || enviando || sinSesion || terminado;
    $('[data-condiciones]').disabled = retenido || enviando || sinSesion || terminado;
    aceptar.disabled = retenido || enviando || sinSesion || terminado;
    enviar.disabled =
      enviando ||
      terminado ||
      sinSesion ||
      (!retenido && sinIncremento) ||
      (!retenido && (cargando || !aceptar.checked || Object.keys(errores).length > 0));
    enviar.textContent = retenido ? 'Reintentar misma publicación' : 'Confirmar y publicar';
    if (enviando) {
      enviar.textContent = 'Publicando…';
    }
    form.setAttribute('aria-busy', String(enviando));
    $('[data-anterior]').disabled = cargando || pagina <= 0;
    $('[data-siguiente]').disabled = cargando || pagina + 1 >= totalPaginas;
    $('[data-referencia]').hidden = !retenido;
    // UXC-9 — sin la clave de idempotencia a la vista: es un identificador
    // interno. Lo que importa es que reintentar repite ESTA publicación.
    $('[data-referencia]').textContent = retenido
      ? 'Guardamos este intento: al reintentar se repite la misma publicación, con el producto, los precios y la comisión que confirmaste, sin crear otra.'
      : '';
  }
  if (!sesion) {
    pedirSesion();
    return;
  }
  form.hidden = false;
  const reglas = await consultarReglas();
  if (reglas && Array.isArray(reglas.duraciones)) {
    for (const d of reglas.duraciones) {
      if (Object.hasOwn(duraciones, d.codigo) && Number.isFinite(Number(d.comision))) {
        duraciones[d.codigo] = { horas: Number(d.horas), comision: Number(d.comision) };
        const etiqueta = $(`[data-comision="${d.codigo}"]`);
        if (etiqueta) {
          const comision = Number(d.comision);
          etiqueta.textContent = `Comisión: ${comision} ${comision === 1 ? 'crédito' : 'créditos'}`;
        }
      }
    }
  }
  // D-43 — el incremento mínimo lo dice el servidor (admin-parametros, 5
  // créditos desde la migración V5): se muestra tal cual, sin una cifra escrita
  // aquí. Sin configurar, no se deja publicar (el servidor respondería 503).
  const avisoIncremento = $('[data-incremento-minimo]');
  if (reglas && reglas.incrementoMinimoConfigurado === false) {
    sinIncremento = true;
    avisoIncremento.textContent = SIN_INCREMENTO;
    avisoIncremento.hidden = false;
  } else if (reglas && reglas.incrementoMinimo !== null && reglas.incrementoMinimo !== undefined) {
    const incremento = Number(reglas.incrementoMinimo);
    if (Number.isFinite(incremento)) {
      avisoIncremento.textContent = `Incremento mínimo: ${textoDeCreditos(incremento)}`;
      avisoIncremento.hidden = false;
    }
  }
  try {
    const guardado = almacenamiento.getItem(claveAlmacen);
    if (guardado) {
      intento = JSON.parse(guardado);
      if (
        !intento?.clave ||
        !intento.solicitud ||
        !Object.hasOwn(DURACIONES, intento.solicitud.duracion)
      ) {
        throw new Error('Intento inválido');
      }
      retenido = true;
      aceptar.checked = true;
      avisar(
        'Hay una publicación pendiente de confirmar. Reintenta la misma operación para conocer su resultado.',
      );
      // PLAYER-07b — el nombre guardado se enseña ya (limpio de ids, por si
      // lo guardó la versión anterior); si el catálogo responde, se pone el suyo.
      nombres.resolver(intento.solicitud.productoId).then(() => actualizar());
    }
  } catch {
    avisar(
      'No se pudo recuperar el intento anterior. Revisa tus subastas antes de continuar.',
      true,
    );
    form.hidden = true;
    return;
  }

  async function cargar(numero) {
    if (cargando || retenido || enviando) {
      return;
    }
    cargando = true;
    $('[data-inventario]').textContent = 'Cargando inventario…';
    $('[data-recargar]').hidden = true;
    actualizar();
    try {
      const respuesta = await consultar(sesion.apodo || sesion.uid, numero, {
        fetchImpl: transporteInventario,
      });
      if (
        !Array.isArray(respuesta?.elementos) ||
        !Number.isInteger(respuesta.numero) ||
        !Number.isInteger(respuesta.totalPaginas)
      ) {
        throw new Error('Inventario inválido');
      }
      elementos = respuesta.elementos;
      pagina = respuesta.numero;
      totalPaginas = respuesta.totalPaginas;
      // PLAYER-07b — el nombre se pide al catálogo antes de pintar las
      // opciones, con un tope corto (ver `nombrar`).
      const nombrando = nombrar(elementos);
      if (nombrando) {
        await nombrando;
      }
      producto.replaceChildren(new Option('Selecciona un producto', ''));
      // UXC-8 — la opción decía «Espada de luz · ARMA · 3f2a…»: la constante
      // del tipo y el identificador interno del elemento. Ahora el tipo en
      // palabras y, si hay dos iguales, cuál es cuál («copia 2»). PLAYER-07b:
      // y el nombre, el del catálogo (ver `etiquetasDelInventario`).
      const porId = etiquetas();
      for (const elemento of elementos) {
        const opcion = new Option(textoDeOpcion(porId.get(elemento.id), elemento), elemento.id);
        opcion.disabled = elemento.disponible === false || !UUID.test(elemento.productoId ?? '');
        producto.appendChild(opcion);
      }
      aceptar.checked = false;
      // UXC-9 — un inventario vacío decía «Página 0 de 0» y nada más. Ahora
      // dice qué pasa y adónde ir; la paginación solo sale si hay páginas.
      const vacio = totalPaginas === 0 || (elementos.length === 0 && pagina === 0);
      let textoInventario = 'Selecciona un elemento para continuar.';
      if (vacio) {
        textoInventario =
          'Tu inventario está vacío: todavía no tienes nada que poner a la venta. Consigue objetos en la tienda o en las misiones.';
      } else if (elementos.length === 0) {
        textoInventario = 'No hay productos en esta página de tu inventario.';
      }
      $('[data-inventario]').textContent = textoInventario;
      $('[data-sin-inventario]').hidden = !vacio;
      $('.publicacion__paginacion').hidden = vacio;
      $('[data-pagina]').textContent = vacio ? '' : `Página ${pagina + 1} de ${totalPaginas}`;
    } catch (error) {
      // UX-R3.11 — antes esta pantalla anunciaba el MISMO fallo dos veces y casi
      // con las mismas palabras: un banner rojo arriba («No se pudo cargar el
      // inventario. Inténtalo de nuevo más tarde.») y, dentro del paso 01, «No
      // se pudo cargar el inventario. Puedes reintentar.». Con el mensaje de
      // validación del campo debajo eran tres avisos en una sola tarjeta.
      // El fallo se cuenta donde esta el hueco que no se lleno, y la salida es
      // el boton -no hace falta que el texto diga que se puede reintentar al
      // lado de un boton que dice «Reintentar carga».
      $('[data-inventario]').textContent = 'No pudimos cargar tu inventario.';
      $('[data-recargar]').hidden = false;
      if (error.status === 401) {
        pedirSesion();
      } else {
        avisar('');
      }
    } finally {
      // El guard de carga/envío impide peticiones simultáneas; controles bloqueados.
      // eslint-disable-next-line require-atomic-updates
      cargando = false;
      actualizar();
    }
  }
  function alEditar(evento) {
    if (evento.target !== aceptar && !retenido) {
      aceptar.checked = false;
    }
    actualizar();
  }
  form.addEventListener('input', alEditar);
  form.addEventListener('change', alEditar);
  $('[data-anterior]').addEventListener('click', () => cargar(pagina - 1));
  $('[data-siguiente]').addEventListener('click', () => cargar(pagina + 1));
  $('[data-recargar]').addEventListener('click', () => cargar(pagina));
  // PLAYER-07b — volver a pedir al catálogo solo lo que falló.
  const reintentarCatalogo = $('[data-reintentar-catalogo]');
  reintentarCatalogo.addEventListener('click', async () => {
    nombres.olvidarFallidos();
    reintentarCatalogo.disabled = true;
    const ids = retenido ? [intento?.solicitud?.productoId] : elementos.map((e) => e.productoId);
    await Promise.all([...new Set(ids)].filter(Boolean).map((id) => nombres.resolver(id)));
    // El boton solo se apaga mientras dura esta consulta.
    reintentarCatalogo.disabled = false;
    etiquetarOpciones();
    actualizar();
  });
  form.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (enviando || terminado || sinSesion || (!retenido && sinIncremento)) {
      return;
    }
    const actual = sesionUtilizable();
    if (!actual || actual.uid !== sesion.uid) {
      pedirSesion();
      return;
    }
    const { seleccionado, duracion, errores } = datos();
    intentado = true;
    if (!retenido && (cargando || !aceptar.checked || Object.keys(errores).length)) {
      actualizar();
      return;
    }
    if (!retenido) {
      try {
        intento = {
          clave: crearClave(),
          // PLAYER-07b — lo que se leyó al confirmar, sin identificadores: si
          // hay que reintentar tras recargar, se vuelve a ver lo mismo.
          nombre: etiquetas().get(seleccionado.id)?.texto ?? SIN_NOMBRE,
          solicitud: {
            elementoInventarioId: seleccionado.id,
            productoId: seleccionado.productoId,
            duracion,
            precioInicial: Number(inicial.value),
            precioCompraInmediata: inmediata.value ? Number(inmediata.value) : null,
          },
        };
        // Guardar ANTES del POST permite recuperar el mismo intento tras recargar.
        // No guarda identidad ni credenciales: reutiliza la sesión existente.
        almacenamiento.setItem(claveAlmacen, JSON.stringify(intento));
      } catch {
        avisar(
          'No se pudo conservar el intento en este navegador. Habilita el almacenamiento de sesión antes de publicar.',
          true,
        );
        return;
      }
    }
    enviando = true;
    retenido = true;
    actualizar();
    avisar('Publicando tu subasta…');
    try {
      const resultado = await publicar(intento.solicitud, intento.clave);
      // El guard de carga/envío impide peticiones simultáneas; controles bloqueados.
      // eslint-disable-next-line require-atomic-updates
      terminado = true;
      // El guard de carga/envío impide peticiones simultáneas; controles bloqueados.
      // eslint-disable-next-line require-atomic-updates
      retenido = false;
      almacenamiento.removeItem(claveAlmacen);
      form.hidden = true;
      avisar(
        `Subasta publicada. Comisión cobrada: ${resultado.comisionCobrado} créditos. Ya está en el mercado para recibir pujas.`,
      );
      // UXC-9 — en vez del identificador, el camino: ver la subasta publicada.
      if (resultado.id) {
        const ver = document.createElement('a');
        ver.href = `./pujas.html?id=${encodeURIComponent(resultado.id)}`;
        ver.className = 'boton boton--primario';
        ver.dataset.accion = 'ver-publicada';
        ver.textContent = 'Ver tu subasta';
        mensaje.after(ver);
      }
    } catch (error) {
      // Un fallo no tipado también puede ocurrir después de que el servidor publique.
      // El guard de carga/envío impide peticiones simultáneas; controles bloqueados.
      // eslint-disable-next-line require-atomic-updates
      retenido = !(error instanceof ErrorPublicacion) || error.incierto;
      if (!retenido) {
        almacenamiento.removeItem(claveAlmacen);
        // El guard de carga/envío impide peticiones simultáneas; controles bloqueados.
        // eslint-disable-next-line require-atomic-updates
        aceptar.checked = false;
      }
      avisar(
        error instanceof ErrorPublicacion
          ? error.message
          : 'No se pudo confirmar el resultado. Reintenta esta misma publicación.',
        true,
      );
      if (error.status === 401) {
        pedirSesion();
      }
    } finally {
      // El guard de carga/envío impide peticiones simultáneas; controles bloqueados.
      // eslint-disable-next-line require-atomic-updates
      enviando = false;
      actualizar();
    }
  });
  actualizar();
  if (!retenido) {
    await cargar(0);
  }
}

/**
 * PLAYER-07b — cuánto se espera al catálogo antes de pintar el inventario. Lo
 * que tarde más llega después y re-etiqueta las opciones solo.
 */
const ESPERA_DEL_CATALOGO_MS = 2500;

/**
 * Cómo se llama un elemento del inventario en esta pantalla.
 *
 * El nombre del catálogo: es el que verá quien puje, porque ms-subastas lo
 * copia del catálogo al publicar. El `nombrePropio` del inventario puede ser
 * otro (uno que se copió al entregar el objeto, un nombre de pruebas como
 * «Arma 1», o el propio id del producto cuando el catálogo no tenía nombre),
 * así que solo sirve de respaldo si el catálogo no responde, y solo si es un
 * nombre: si es un código, «Objeto sin nombre». Nunca un identificador.
 *
 * @param {object} elemento `ElementoInventario`
 * @param {{estado: string, nombre: string|null}|null} conocido lo que se sabe del catálogo
 * @returns {{texto: string, origen: 'catalogo'|'inventario'|'sin-nombre'}}
 */
function nombreParaElegir(elemento, conocido) {
  const tipo = nombreDelTipo(elemento.tipo);
  if (conocido?.estado === 'ok') {
    return { texto: `${conocido.nombre} · ${tipo}`, origen: 'catalogo' };
  }
  const propio = nombreLegible(elemento.nombrePropio, {
    respaldo: '',
    identificadores: [elemento.id, elemento.productoId],
  });
  if (propio) {
    return { texto: `${propio} · ${tipo}`, origen: 'inventario' };
  }
  return { texto: `${SIN_NOMBRE} · ${tipo}`, origen: 'sin-nombre' };
}

/**
 * Las etiquetas de una página de inventario, por id de elemento. Si dos se
 * leerían igual, se numeran («copia 2»): es lo único que las distingue sin
 * enseñar su identificador.
 *
 * @param {Array<object>} lista
 * @param {(productoId: string) => object|null} conocidoDe
 * @returns {Map<string, {texto: string, origen: string}>}
 */
function etiquetasDelInventario(lista, conocidoDe) {
  const base = lista.map((elemento) => ({
    elemento,
    ...nombreParaElegir(elemento, conocidoDe(elemento.productoId)),
  }));
  const repeticiones = new Map();
  for (const { texto } of base) {
    repeticiones.set(texto, (repeticiones.get(texto) ?? 0) + 1);
  }
  const vistas = new Map();
  const porId = new Map();
  for (const { elemento, texto, origen } of base) {
    const copia = (vistas.get(texto) ?? 0) + 1;
    vistas.set(texto, copia);
    porId.set(elemento.id, {
      texto: repeticiones.get(texto) > 1 ? `${texto} · copia ${copia}` : texto,
      origen,
    });
  }
  return porId;
}

/** El texto de la opción: la etiqueta y, si no se puede elegir, por qué. */
function textoDeOpcion(etiqueta, elemento) {
  const texto = etiqueta?.texto ?? `${SIN_NOMBRE} · ${nombreDelTipo(elemento.tipo)}`;
  return `${texto}${elemento.disponible === false ? ' · No disponible' : ''}`;
}

/**
 * El nombre de una publicación pendiente de confirmar (guardada antes del
 * POST). La versión anterior guardaba «Espada de luz · ARMA · 7c9e…»: se
 * enseña sin los identificadores y con el tipo en palabras. Si el catálogo ya
 * respondió, su nombre sustituye al guardado.
 *
 * @param {{nombre?: string}|null} intento
 * @param {{estado: string, nombre: string|null}|null} conocido
 * @returns {{texto: string, origen: 'catalogo'|'guardado'}}
 */
function nombreDelIntento(intento, conocido) {
  const [primero = '', ...resto] = String(intento?.nombre ?? '').split(' · ');
  const nombre = nombreLegible(primero);
  const detalles = resto
    .map((parte) => parte.trim())
    .filter((parte) => parte && nombreLegible(parte, { respaldo: '' }) !== '')
    .map((parte) => (Object.hasOwn(NOMBRE_DEL_TIPO, parte) ? nombreDelTipo(parte) : parte));
  const principal = conocido?.estado === 'ok' ? conocido.nombre : nombre;
  return {
    texto: [principal, ...detalles].join(' · '),
    origen: conocido?.estado === 'ok' ? 'catalogo' : 'guardado',
  };
}
