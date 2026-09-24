// Registro de auditoría — HU-AUD-002 / HU-AUD-004 (exportar)
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
    btnExportar: document.getElementById('btn-exportar'),
    btnAnterior: document.getElementById('btn-anterior'),
    btnSiguiente: document.getElementById('btn-siguiente'),
    paginaActual: document.getElementById('auditoria-pagina-actual'),
  };

  const MESES = [
    'ene', 'feb', 'mar', 'abr', 'may', 'jun',
    'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
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

  // Filtros comunes a la consulta paginada y a la exportación (HU-AUD-004):
  // mismos criterios, para que "exportar lo que estoy viendo" sea literal.
  function parametrosFiltro() {
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

    return params;
  }

  function construirUrl() {
    const params = parametrosFiltro();
    params.set('page', String(estado.pagina));
    params.set('size', String(TAMANO_PAGINA));
    return `${API_BASE}?${params.toString()}`;
  }

  function construirUrlExportacion() {
    const params = parametrosFiltro();
    return `${API_BASE}/exportar?${params.toString()}`;
  }

  function renderFilas(registros) {
    el.tbody.innerHTML = '';

    registros.forEach((registro) => {
      const tr = document.createElement('tr');

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

  function tokenDeSesion() {
    return sessionStorage.getItem('nexus.token');
  }

  async function cargar() {
    el.tbody.innerHTML = '';
    mostrarEstado('Cargando...', 'carga');

    try {
      const token = tokenDeSesion();
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
    } catch {
      mostrarEstado('Error de red al consultar la auditoría.', 'error');
    }
  }

  /**
   * Exporta el registro filtrado a PDF (HU-AUD-004) y dispara la descarga
   * en el navegador. Reusa exactamente los mismos filtros que la consulta
   * en pantalla.
   *
   * Si el backend responde 422, significa que el filtro actual trae más
   * registros de los que se pueden exportar en una operación: se muestra
   * el detalle del error (que pide acotar el rango) en vez de intentar
   * descargar algo.
   */
  async function exportar() {
    const boton = el.btnExportar;
    const textoOriginalBoton = boton.textContent;
    boton.disabled = true;
    boton.textContent = 'Exportando…';

    try {
      const token = tokenDeSesion();
      const respuesta = await fetch(construirUrlExportacion(), {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });

      if (respuesta.status === 401) {
        mostrarEstado('Inicia sesión como Super Administrador para exportar.', 'error');
        return;
      }

      if (respuesta.status === 403) {
        mostrarEstado('No tienes permisos de Super Administrador para exportar.', 'error');
        return;
      }

      if (respuesta.status === 422) {
        let detalle = 'El filtro actual tiene demasiados registros para exportar. Acota el rango de fechas.';
        try {
          const problema = await respuesta.json();
          if (problema && problema.detail) {
            detalle = problema.detail;
          }
        } catch {
          // Si el cuerpo no es JSON, se queda el mensaje por defecto.
        }
        mostrarEstado(detalle, 'error');
        return;
      }

      if (!respuesta.ok) {
        mostrarEstado('No se pudo exportar el registro de auditoría.', 'error');
        return;
      }

      const blob = await respuesta.blob();
      const nombreArchivo = nombreDesdeContentDisposition(respuesta.headers.get('Content-Disposition'))
        ?? `auditoria-${Date.now()}.pdf`;

      const url = URL.createObjectURL(blob);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = nombreArchivo;
      document.body.appendChild(enlace);
      enlace.click();// Registro de auditoría — HU-AUD-002 / HU-AUD-004 (exportar)
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
          btnExportar: document.getElementById('btn-exportar'),
          btnAnterior: document.getElementById('btn-anterior'),
          btnSiguiente: document.getElementById('btn-siguiente'),
          paginaActual: document.getElementById('auditoria-pagina-actual'),
        };

        const MESES = [
          'ene', 'feb', 'mar', 'abr', 'may', 'jun',
          'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
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

        // Filtros comunes a la consulta paginada y a la exportación (HU-AUD-004):
        // mismos criterios, para que "exportar lo que estoy viendo" sea literal.
        function parametrosFiltro() {
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

          return params;
        }

        function construirUrl() {
          const params = parametrosFiltro();
          params.set('page', String(estado.pagina));
          params.set('size', String(TAMANO_PAGINA));
          return `${API_BASE}?${params.toString()}`;
        }

        function construirUrlExportacion() {
          const params = parametrosFiltro();
          return `${API_BASE}/exportar?${params.toString()}`;
        }

        function renderFilas(registros) {
          el.tbody.innerHTML = '';

          registros.forEach((registro) => {
            const tr = document.createElement('tr');

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

        function tokenDeSesion() {
          return sessionStorage.getItem('nexus.token');
        }

        async function cargar() {
          el.tbody.innerHTML = '';
          mostrarEstado('Cargando...', 'carga');

          try {
            const token = tokenDeSesion();
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
          } catch {
            mostrarEstado('Error de red al consultar la auditoría.', 'error');
          }
        }

        /**
         * Exporta el registro filtrado a PDF (HU-AUD-004) y dispara la descarga
         * en el navegador. Reusa exactamente los mismos filtros que la consulta
         * en pantalla.
         *
         * Si el backend responde 422, significa que el filtro actual trae más
         * registros de los que se pueden exportar en una operación: se muestra
         * el detalle del error (que pide acotar el rango) en vez de intentar
         * descargar algo.
         */
        async function exportar() {
          const boton = el.btnExportar;
          const textoOriginalBoton = boton.textContent;
          boton.disabled = true;
          boton.textContent = 'Exportando…';

          try {
            const token = tokenDeSesion();
            const respuesta = await fetch(construirUrlExportacion(), {
              headers: token ? { Authorization: `Bearer ${token}` } : {},
            });

            if (respuesta.status === 401) {
              mostrarEstado('Inicia sesión como Super Administrador para exportar.', 'error');
              return;
            }

            if (respuesta.status === 403) {
              mostrarEstado('No tienes permisos de Super Administrador para exportar.', 'error');
              return;
            }

            if (respuesta.status === 422) {
              let detalle = 'El filtro actual tiene demasiados registros para exportar. Acota el rango de fechas.';
              try {
                const problema = await respuesta.json();
                if (problema && problema.detail) {
                  detalle = problema.detail;
                }
              } catch {
                // Si el cuerpo no es JSON, se queda el mensaje por defecto.
              }
              mostrarEstado(detalle, 'error');
              return;
            }

            if (!respuesta.ok) {
              mostrarEstado('No se pudo exportar el registro de auditoría.', 'error');
              return;
            }

            const blob = await respuesta.blob();
            const nombreArchivo = nombreDesdeContentDisposition(respuesta.headers.get('Content-Disposition'))
              ?? `auditoria-${Date.now()}.pdf`;

            const url = URL.createObjectURL(blob);
            const enlace = document.createElement('a');
            enlace.href = url;
            enlace.download = nombreArchivo;
            document.body.appendChild(enlace);
            enlace.click();
            document.body.removeChild(enlace);
            URL.revokeObjectURL(url);

            mostrarEstado('Exportación descargada.', 'exito');
          } catch {
            mostrarEstado('Error de red al exportar la auditoría.', 'error');
          } finally {
            boton.disabled = false;
            boton.textContent = textoOriginalBoton;
          }
        }

        function nombreDesdeContentDisposition(valorCabecera) {
          if (!valorCabecera) {
            return null;
          }
          const coincidencia = /filename="?([^"]+)"?/.exec(valorCabecera);
          return coincidencia ? coincidencia[1] : null;
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

        el.btnExportar.addEventListener('click', () => {
          exportar();
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

      document.body.removeChild(enlace);
      URL.revokeObjectURL(url);

      mostrarEstado('Exportación descargada.', 'exito');
    } catch {
      mostrarEstado('Error de red al exportar la auditoría.', 'error');
    } finally {
      boton.disabled = false;
      boton.textContent = textoOriginalBoton;
    }
  }

  function nombreDesdeContentDisposition(valorCabecera) {
    if (!valorCabecera) {
      return null;
    }
    const coincidencia = /filename="?([^"]+)"?/.exec(valorCabecera);
    return coincidencia ? coincidencia[1] : null;
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

  el.btnExportar.addEventListener('click', () => {
    exportar();
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
