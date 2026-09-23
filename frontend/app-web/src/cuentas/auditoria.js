// Registro de auditoría — HU-AUD-002
// JS vanilla, siguiendo el patrón .estado[hidden] ya usado en el equipo
// (visto en tema-cuentas.css: .estado.carga / .estado.error / .estado.vacio).

(() => {
  const API_BASE = '/api/v1/admin/auditoria';
  const TAMANO_PAGINA = 20;

  const estado = {
    pagina: 0, // Spring Data Page usa índice de página desde 0
    totalPaginas: 1,
  };

  const el = {
    tbody: document.getElementById('auditoria-tbody'),
    estado: document.getElementById('auditoria-estado'),
    filtroAdministrador: document.getElementById('filtro-administrador'),
    filtroTipoAccion: document.getElementById('filtro-tipo-accion'),
    filtroDesde: document.getElementById('filtro-desde'),
    filtroHasta: document.getElementById('filtro-hasta'),
    btnFiltrar: document.getElementById('btn-filtrar'),
    btnLimpiar: document.getElementById('btn-limpiar'),
    btnAnterior: document.getElementById('btn-anterior'),
    btnSiguiente: document.getElementById('btn-siguiente'),
    paginaActual: document.getElementById('auditoria-pagina-actual'),
  };

  const MESES = [
    'ene',
    'feb',
    'mar',
    'abr',
    'may',
    'jun',
    'jul',
    'ago',
    'sep',
    'oct',
    'nov',
    'dic',
  ];

  function formatearFecha(isoString) {
    if (!isoString) {
      return '—';
    }
    const fecha = new Date(isoString);
    if (Number.isNaN(fecha.getTime())) {
      return isoString;
    }
    const dia = fecha.getDate();
    const mes = MESES[fecha.getMonth()];
    const horas = String(fecha.getHours()).padStart(2, '0');
    const minutos = String(fecha.getMinutes()).padStart(2, '0');
    return `${dia} ${mes} · ${horas}:${minutos}`;
  }

  function claseBadge(tipoAccion) {
    return `badge badge--${String(tipoAccion || 'otro').toLowerCase()}`;
  }

  function textoCorto(valor, maxLargo = 60) {
    if (!valor) {
      return '—';
    }
    return valor.length > maxLargo ? `${valor.slice(0, maxLargo)}…` : valor;
  }

  // Sigue el mismo patrón que .estado.carga/.error/.vacio de tema-cuentas.css
  function mostrarEstado(texto, tipo) {
    el.estado.hidden = false;
    el.estado.textContent = texto;
    el.estado.classList.remove('carga', 'error', 'vacio', 'exito');
    if (tipo) {
      el.estado.classList.add(tipo);
    }
  }

  function ocultarEstado() {
    el.estado.hidden = true;
    el.estado.textContent = '';
  }

  function construirUrl() {
    const params = new URLSearchParams();

    const administradorId = el.filtroAdministrador.value.trim();
    const tipoAccion = el.filtroTipoAccion.value;
    const desde = el.filtroDesde.value;
    const hasta = el.filtroHasta.value;

    if (administradorId) {
      params.set('administradorId', administradorId);
    }
    if (tipoAccion) {
      params.set('tipoAccion', tipoAccion);
    }
    if (desde) {
      params.set('desde', `${desde}T00:00:00Z`);
    }
    if (hasta) {
      params.set('hasta', `${hasta}T23:59:59Z`);
    }

    params.set('page', String(estado.pagina));
    params.set('size', String(TAMANO_PAGINA));

    return `${API_BASE}?${params.toString()}`;
  }

  function renderFilas(registros) {
    el.tbody.innerHTML = '';

    registros.forEach((registro) => {
      const tr = document.createElement('tr');

      // UX-R2.8 — esto era una plantilla con nueve interpolaciones dentro de
      // `innerHTML`. `motivo`, `valorAnterior` y `valorNuevo` son texto que
      // escribio una persona, y ademas iban tambien dentro de `title="..."`,
      // donde una comilla cierra el atributo. Un registro de auditoria es el
      // ultimo sitio donde uno quiere marcado inyectado: lo lee un
      // administrador, con sesion de administrador.
      const celda = (texto, clase, titulo) => {
        const td = document.createElement('td');
        if (clase) {
          td.className = clase;
        }
        if (titulo) {
          td.title = titulo;
        }
        td.textContent = texto;
        return td;
      };

      const distintivo = document.createElement('span');
      distintivo.className = claseBadge(registro.tipoAccion);
      distintivo.textContent = registro.tipoAccion ?? '—';
      const celdaAccion = document.createElement('td');
      celdaAccion.append(distintivo);

      tr.append(
        celda(formatearFecha(registro.fechaHora), 'celda-fecha'),
        celda(registro.administrador ?? '—'),
        celdaAccion,
        celda(registro.afectado ?? '—'),
        celda(textoCorto(registro.valorAnterior), 'celda-valor', registro.valorAnterior ?? ''),
        celda(textoCorto(registro.valorNuevo), 'celda-valor', registro.valorNuevo ?? ''),
        celda(textoCorto(registro.motivo), 'celda-valor', registro.motivo ?? ''),
        celda(registro.ipOrigen ?? '—'),
      );
      el.tbody.appendChild(tr);
    });
  }

  function actualizarPaginacion(pagina, totalPaginas) {
    estado.pagina = pagina;
    estado.totalPaginas = Math.max(totalPaginas, 1);

    el.paginaActual.textContent = `Página ${estado.pagina + 1} de ${estado.totalPaginas}`;
    el.btnAnterior.disabled = estado.pagina <= 0;
    el.btnSiguiente.disabled = estado.pagina >= estado.totalPaginas - 1;
  }

  async function cargar() {
    el.tbody.innerHTML = '';
    mostrarEstado('Cargando...', 'carga');

    try {
      // El JWT de la sesion, el mismo que lleva el resto de la API: la
      // bitacora la consulta un SUPER_ADMINISTRADOR con su token (ADR-002).
      // Antes iba `credentials: 'include'`, y ms-cumplimiento no tiene cookies.
      const token = sessionStorage.getItem('nexus.token');
      const respuesta = await fetch(construirUrl(), {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });

      if (respuesta.status === 401) {
        mostrarEstado(
          'Inicia sesión como Super Administrador para consultar este registro.',
          'error',
        );
        el.btnAnterior.disabled = true;
        el.btnSiguiente.disabled = true;
        return;
      }

      if (respuesta.status === 403) {
        mostrarEstado(
          'No tienes permisos de Super Administrador para consultar este registro.',
          'error',
        );
        el.btnAnterior.disabled = true;
        el.btnSiguiente.disabled = true;
        return;
      }

      if (!respuesta.ok) {
        mostrarEstado('No se pudo cargar el registro de auditoría.', 'error');
        return;
      }

      const datos = await respuesta.json();

      // Soporta tanto Page<T> de Spring Data como un arreglo simple.
      const registros = datos.content ?? datos;
      const totalPaginas = datos.totalPages ?? 1;
      const paginaActual = datos.number ?? estado.pagina;

      if (!registros || registros.length === 0) {
        mostrarEstado('No hay registros con esos filtros.', 'vacio');
      } else {
        ocultarEstado();
        renderFilas(registros);
      }

      actualizarPaginacion(paginaActual, totalPaginas);
      // OJO: este bloque atrapa cualquier excepcion, no solo las de red, y
      // siempre muestra el mismo mensaje. Ver la nota del PR de saneamiento.
    } catch {
      mostrarEstado('Error de red al consultar la auditoría.', 'error');
    }
  }

  el.btnFiltrar.addEventListener('click', () => {
    estado.pagina = 0;
    cargar();
  });

  el.btnLimpiar.addEventListener('click', () => {
    el.filtroAdministrador.value = '';
    el.filtroTipoAccion.value = '';
    el.filtroDesde.value = '';
    el.filtroHasta.value = '';
    estado.pagina = 0;
    cargar();
  });

  el.btnAnterior.addEventListener('click', () => {
    if (estado.pagina > 0) {
      estado.pagina -= 1;
      cargar();
    }
  });

  el.btnSiguiente.addEventListener('click', () => {
    if (estado.pagina < estado.totalPaginas - 1) {
      estado.pagina += 1;
      cargar();
    }
  });

  cargar();
})();
