import { montarCabecera, leerSesion } from '../comun/cabecera-app.js';
import { cuerpoDelToken } from '../comun/identidad.js';
import { baseDeApi } from '../comun/base-api.js';
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { consultarPagina } from '../contenido/inventario/cliente-inventario.js';
import {
  publicarSubasta,
  crearClavePublicacion,
  ErrorPublicacion,
} from './cliente-publicacion-subastas.js';

const DURACIONES = { '24H': { horas: 24, comision: 1 }, '48H': { horas: 48, comision: 3 } };
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
    if (!Number.isFinite(compra) || compra <= 0 || compra < precio) {
      errores.inmediata =
        'La compra inmediata debe ser positiva y mayor o igual al precio inicial.';
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
              <nav class="publicacion__paginacion" aria-label="Páginas del inventario">
                <button type="button" data-anterior class="boton boton--secundario">Anterior</button>
                <span data-pagina></span>
                <button type="button" data-siguiente class="boton boton--secundario">Siguiente</button>
                <button type="button" data-recargar class="boton boton--secundario" hidden>Reintentar carga</button>
              </nav>
            </fieldset>
            <p class="publicacion__ayuda">Los productos no disponibles aparecen deshabilitados. Al publicar se comprobarán también el uso, la propiedad y si el producto es subastable.</p>
          </section>
          <section class="publicacion__panel" aria-labelledby="titulo-condiciones">
            <h2 id="titulo-condiciones"><span class="publicacion__paso">02</span> Condiciones de publicación</h2>
            <fieldset data-condiciones class="pila">
              <fieldset aria-describedby="error-duracion">
                <legend>Duración y comisión</legend>
                <div class="publicacion__duraciones">
                  <label class="publicacion__duracion"><input type="radio" name="duracion" value="24H" checked required /><span>24 horas<small>Comisión: 1 crédito</small></span></label>
                  <label class="publicacion__duracion"><input type="radio" name="duracion" value="48H" required /><span>48 horas<small>Comisión: 3 créditos</small></span></label>
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
  const almacenamiento = globalThis.sessionStorage;
  const claveAlmacen = sesion ? `nexus.hu-sub-001.intento:${sesion.uid}` : null;

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
    const configuracion = DURACIONES[solicitud?.duracion ?? duracion];
    const nombreSeleccionado = seleccionado
      ? `${seleccionado.nombrePropio} · ${seleccionado.tipo} · ${seleccionado.id}`
      : 'Sin seleccionar';
    $('[data-resumen-producto]').textContent = retenido ? intento.nombre : nombreSeleccionado;
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
      (!retenido && (cargando || !aceptar.checked || Object.keys(errores).length > 0));
    enviar.textContent = retenido ? 'Reintentar misma publicación' : 'Confirmar y publicar';
    if (enviando) {
      enviar.textContent = 'Publicando…';
    }
    form.setAttribute('aria-busy', String(enviando));
    $('[data-anterior]').disabled = cargando || pagina <= 0;
    $('[data-siguiente]').disabled = cargando || pagina + 1 >= totalPaginas;
    $('[data-referencia]').hidden = !retenido;
    $('[data-referencia]').textContent = retenido
      ? `Referencia del intento: ${intento.clave}. Se conservan el producto, los precios y la comisión confirmados.`
      : '';
  }
  if (!sesion) {
    pedirSesion();
    return;
  }
  form.hidden = false;
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
      producto.replaceChildren(new Option('Selecciona un producto', ''));
      for (const elemento of elementos) {
        const opcion = new Option(
          `${elemento.nombrePropio} · ${elemento.tipo} · ${elemento.id}${elemento.disponible === false ? ' · No disponible' : ''}`,
          elemento.id,
        );
        opcion.disabled = elemento.disponible === false || !UUID.test(elemento.productoId ?? '');
        producto.appendChild(opcion);
      }
      aceptar.checked = false;
      $('[data-inventario]').textContent = elementos.length
        ? 'Selecciona un elemento para continuar.'
        : 'No hay productos en esta página de tu inventario.';
      $('[data-pagina]').textContent = `Página ${totalPaginas ? pagina + 1 : 0} de ${totalPaginas}`;
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
  form.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    if (enviando || terminado || sinSesion) {
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
          nombre: `${seleccionado.nombrePropio} · ${seleccionado.tipo} · ${seleccionado.id}`,
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
        `Subasta publicada correctamente. Comisión cobrada: ${resultado.comisionCobrado} créditos. Identificador: ${resultado.id}. Ya puedes volver a Subastas.`,
      );
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
