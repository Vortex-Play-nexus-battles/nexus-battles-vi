/**
 * Subastas - Pruebas unitarias y de integración DOM (HU-SUB-004)
 */

import { jest } from '@jest/globals';
import {
  calcularSaldoLibre,
  calcularSaldoRetenido,
  calcularMinimoPuja,
  validarPuja,
  validarLimiteAuto,
  calcularComparacionHeroe,
  formatearCreditos,
  formatearTiempo,
  calcularSumaTopesAuto,
  verificarSobreCompromiso,
  calcularBalanceNetoCierre,
  generarConsejoTactico,
  calcularEstadoTopesConcurrencia,
  ControladorSubastas,
  SUBASTAS_INICIALES,
  HEROES_BASE,
} from './pujas.js';

describe('HU-SUB-004 - Reglas de Negocio de Subastas y Pujas', () => {
  describe('Cálculos y formateo', () => {
    test('formatea cifras con separador de miles es-CO', () => {
      expect(formatearCreditos(1350)).toBe('1.350');
      expect(formatearCreditos(0)).toBe('0');
    });

    test('formatea tiempo en minutos y segundos', () => {
      expect(formatearTiempo(38)).toBe('0:38');
      expect(formatearTiempo(184)).toBe('3:04');
      expect(formatearTiempo(3665)).toBe('1 h 1 m');
      expect(formatearTiempo(0)).toBe('Cerrada');
    });

    test('calcula saldo retenido y saldo libre correctamente', () => {
      const subastas = [
        { id: '1', retenido: 1000 },
        { id: '2', retenido: 500 },
        { id: '3', retenido: 0 },
      ];
      expect(calcularSaldoRetenido(subastas)).toBe(1500);
      expect(calcularSaldoLibre(6000, subastas)).toBe(4500);
    });

    test('calcula el incremento mínimo correctamente', () => {
      expect(calcularMinimoPuja(1000, 50)).toBe(1050);
      expect(calcularMinimoPuja(0, 50)).toBe(50);
    });
  });

  describe('Validación de pujas', () => {
    const subastaEjemplo = {
      id: 'sub-1',
      oferta: 1000,
      retenido: 1000,
      segundosRestantes: 60,
    };

    test('acepta una puja que cumple oferta + incremento y saldo suficiente', () => {
      const res = validarPuja(1050, subastaEjemplo, 2000, 50, 0);
      expect(res.valida).toBe(true);
    });

    test('rechaza una puja inferior a la oferta actual + incremento mínimo', () => {
      const res = validarPuja(1020, subastaEjemplo, 2000, 50, 0);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('al menos 1.050 cr');
    });

    test('rechaza una puja si el saldo disponible no alcanza', () => {
      // Saldo libre 0 + retenido 1000 = 1000 disponibles. Intentar pujar 1100 debe fallar
      const res = validarPuja(1100, subastaEjemplo, 0, 50, 0);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('Saldo insuficiente');
    });

    test('rechaza una puja si la subasta está cerrada', () => {
      const subCerrada = { ...subastaEjemplo, segundosRestantes: 0 };
      const res = validarPuja(1100, subCerrada, 5000, 50, 0);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('cerrada');
    });

    test('rechaza una puja si el intervalo de espera de 5 s no ha culminado', () => {
      const res = validarPuja(1100, subastaEjemplo, 5000, 50, 4);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('esperar 4 s');
    });
  });

  describe('Validación de puja automática', () => {
    const subasta = { id: 'sub-1', oferta: 800, retenido: 0 };

    test('acepta un límite superior a la oferta mínima y dentro del saldo', () => {
      const res = validarLimiteAuto(1200, subasta, 2000, 50);
      expect(res.valida).toBe(true);
    });

    test('rechaza si el tope es inferior a la oferta + incremento', () => {
      const res = validarLimiteAuto(820, subasta, 2000, 50);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('al menos 850 cr');
    });

    test('rechaza si el tope excede el saldo libre', () => {
      const res = validarLimiteAuto(3000, subasta, 1000, 50);
      expect(res.valida).toBe(false);
      expect(res.motivo).toContain('Saldo insuficiente');
    });
  });

  describe('Comparación de héroes y alerta de nivel (RN-INV-004)', () => {
    const item = { nivel: 24, aporte: { poder: 10, vida: 50, defensa: 5 } };

    test('alerta cuando el héroe no tiene el nivel suficiente', () => {
      const heroeBajo = { nivel: 20, stats: { poder: 100, vida: 500, defensa: 30 } };
      const comp = calcularComparacionHeroe(heroeBajo, item);
      expect(comp.nivelInsuficiente).toBe(true);
      expect(comp.deltaNivel).toBe(4);
    });

    test('aprueba cuando el héroe cumple o supera el nivel', () => {
      const heroeAlto = { nivel: 26, stats: { poder: 100, vida: 500, defensa: 30 } };
      const comp = calcularComparacionHeroe(heroeAlto, item);
      expect(comp.nivelInsuficiente).toBe(false);
      expect(comp.deltaNivel).toBe(-2);
    });

    test('calcula correctamente los deltas de estadísticas', () => {
      const heroe = { nivel: 25, stats: { poder: 100, vida: 500, defensa: 30 } };
      const comp = calcularComparacionHeroe(heroe, item);
      expect(comp.comparaciones).toEqual([
        { stat: 'Poder', actual: 100, nuevo: 110, delta: 10 },
        { stat: 'Vida', actual: 500, nuevo: 550, delta: 50 },
        { stat: 'Defensa', actual: 30, nuevo: 35, delta: 5 },
      ]);
    });
  });
});

describe('ControladorSubastas - Interacción y Flujo DOM', () => {
  let contenedor;
  let controlador;

  beforeEach(() => {
    contenedor = document.createElement('div');
    document.body.appendChild(contenedor);
    controlador = new ControladorSubastas({
      contenedor,
      subastas: SUBASTAS_INICIALES,
      heroes: HEROES_BASE,
    });
    controlador.render();
  });

  afterEach(() => {
    controlador.destruir();
    contenedor.remove();
  });

  test('renderiza el listado inicial de subastas con sus tarjetas', () => {
    const tarjetas = contenedor.querySelectorAll('.tarjeta-subasta');
    expect(tarjetas.length).toBe(SUBASTAS_INICIALES.length);
    expect(contenedor.textContent).toContain('Subastas y Pujas');
  });

  test('soporta los 4 estados obligatorios de RNF-USA-003', () => {
    // Estado Carga
    controlador.estadoDatos = 'carga';
    controlador.render();
    expect(contenedor.querySelector('.estado-carga')).not.toBeNull();

    // Estado Error
    controlador.estadoDatos = 'error';
    controlador.mensajeError = 'Fallo de conexión';
    controlador.render();
    expect(contenedor.querySelector('.estado-error')).not.toBeNull();
    expect(contenedor.textContent).toContain('Fallo de conexión');

    // Estado Vacío
    controlador.estadoDatos = 'vacio';
    controlador.render();
    expect(contenedor.querySelector('.estado-vacio')).not.toBeNull();

    // Estado Éxito
    controlador.estadoDatos = 'exito';
    controlador.render();
    expect(contenedor.querySelector('.grid-subastas')).not.toBeNull();
  });

  test('navega de lista a detalle al pulsar una subasta y permite volver', () => {
    expect(controlador.vista).toBe('lista');

    // Abrir hacha
    controlador.abrirDetalle('hacha-obsidiana');
    expect(controlador.vista).toBe('detalle');
    expect(contenedor.querySelector('.vista-detalle')).not.toBeNull();
    expect(contenedor.textContent).toContain('Hacha de Obsidiana Fracturada');

    // Volver
    const btnVolver = contenedor.querySelector('#btn-volver');
    btnVolver.click();
    expect(controlador.vista).toBe('lista');
    expect(contenedor.querySelector('.grid-subastas')).not.toBeNull();
  });

  test('permite cambiar el héroe para comparar estadísticas en el detalle', () => {
    controlador.abrirDetalle('hacha-obsidiana'); // Pide nivel 24
    expect(controlador.heroeId).toBe('kaelen'); // Kaelen es nivel 26 -> compatible
    expect(contenedor.textContent).toContain('Compatible');

    // Cambiar a Lyra (nivel 21)
    controlador.seleccionarHeroe('lyra');
    expect(controlador.heroeId).toBe('lyra');
    expect(contenedor.textContent).toContain('Nivel insuficiente');
  });

  test('realizar una puja válida actualiza la oferta vigente y el historial', () => {
    controlador.abrirDetalle('hacha-obsidiana');
    const ofertaPrevia = controlador.getSubastaActiva().oferta; // 1350
    const nuevaOferta = ofertaPrevia + 100; // 1450

    const exito = controlador.pujar(nuevaOferta);
    expect(exito).toBe(true);

    const subActualizada = controlador.getSubastaActiva();
    expect(subActualizada.oferta).toBe(nuevaOferta);
    expect(subActualizada.ganando).toBe(true);
    expect(subActualizada.historial[0].monto).toBe(nuevaOferta);
    expect(subActualizada.historial[0].esTu).toBe(true);
  });

  test('permite configurar y desactivar una puja automática', () => {
    controlador.abrirDetalle('hacha-obsidiana');
    const sub = controlador.getSubastaActiva();

    controlador.configurarAutoPuja(2000);
    expect(sub.autoLimite).toBe(2000);

    controlador.desactivarAutoPuja();
    expect(sub.autoLimite).toBe(0);
  });

  test('flujo de compra inmediata exige confirmación y cierra la subasta', () => {
    controlador.abrirDetalle('hacha-obsidiana');
    const sub = controlador.getSubastaActiva();

    // Solicitar compra abre modal
    controlador.solicitarCompraInmediata();
    expect(controlador.confirmandoCompra).toBe(true);
    expect(contenedor.querySelector('#modal-compra-inmediata')).not.toBeNull();

    // Confirmar compra
    controlador.confirmarCompraInmediata();
    expect(controlador.confirmandoCompra).toBe(false);
    expect(controlador.resultadoCierre).toBe('comprada');
    expect(sub.segundosRestantes).toBe(0);
    expect(contenedor.textContent).toContain('¡Has comprado este objeto de inmediato!');
  });

  describe('Navegación por Pestañas / Modos', () => {
    test('permite alternar entre las 4 pestañas: Explorar, Mis Subastas, Detalle y Cierre Múltiple', () => {
      // Estado inicial en lista/explorar
      expect(controlador.vista).toBe('lista');
      expect(contenedor.querySelector('.tab-btn[data-tab="explorar"]')).not.toBeNull();
      expect(contenedor.querySelector('.tab-btn[data-tab="detalle"]')).not.toBeNull();

      // Ir a Detalle mediante pestaña
      const tabDetalle = contenedor.querySelector('.tab-btn[data-tab="detalle"]');
      tabDetalle.click();
      expect(controlador.vista).toBe('detalle');
      expect(contenedor.querySelector('.vista-detalle')).not.toBeNull();

      // Ir a Mis Subastas
      const tabMisSubastas = contenedor.querySelector('.tab-btn[data-tab="mis-subastas"]');
      expect(tabMisSubastas).not.toBeNull();
      tabMisSubastas.click();
      expect(controlador.vista).toBe('mis-subastas');
      expect(contenedor.querySelector('.vista-mis-subastas')).not.toBeNull();

      // Ir a Cierre Múltiple
      const tabCierre = contenedor.querySelector('.tab-btn[data-tab="cierre-multiple"]');
      expect(tabCierre).not.toBeNull();
      tabCierre.click();
      expect(controlador.vista).toBe('cierre-multiple');
      expect(contenedor.querySelector('.vista-cierre-multiple')).not.toBeNull();

      // Volver a Explorar
      const tabExplorar = contenedor.querySelector('.tab-btn[data-tab="explorar"]');
      expect(tabExplorar).not.toBeNull();
      tabExplorar.click();
      expect(controlador.vista).toBe('explorar');
      expect(contenedor.querySelector('.grid-subastas')).not.toBeNull();
    });

    test('el botón volver en detalle respeta el origen de navegación', () => {
      // Entrar a detalle desde Mis Subastas
      controlador.abrirMisSubastas();
      controlador.abrirDetalle('grebas-centinela');
      expect(controlador.origenVista).toBe('mis-subastas');
      const btnVolver = contenedor.querySelector('#btn-volver');
      expect(btnVolver.textContent).toContain('Volver a mis subastas');
      btnVolver.click();
      expect(controlador.vista).toBe('mis-subastas');

      // Botón lateral 'Ver todas mis subastas'
      controlador.abrirDetalle('hacha-obsidiana');
      const btnLateral = contenedor.querySelector('#btn-ver-todas-mis-subastas');
      expect(btnLateral).not.toBeNull();
      btnLateral.click();
      expect(controlador.vista).toBe('mis-subastas');
    });
  });

  describe('Vista «Mis Subastas» - Finanzas y Concurrencia', () => {
    beforeEach(() => {
      controlador.abrirMisSubastas();
    });

    test('renderiza el panel de créditos con barra segmentada y leyenda', () => {
      expect(contenedor.querySelector('.panel-creditos-segmentada')).not.toBeNull();
      expect(contenedor.querySelector('.barra-segmentada-tramos')).not.toBeNull();
      expect(contenedor.querySelectorAll('.tramo-subasta').length).toBeGreaterThan(0);
      expect(contenedor.querySelector('.leyenda-tramos')).not.toBeNull();
      expect(contenedor.textContent).toContain('Tienes en total');
      expect(contenedor.textContent).toContain('Retenido en subastas');
      expect(contenedor.textContent).toContain('Libre para pujar');
    });

    test('renderiza medidores de topes de concurrencia y alerta reactiva al 80%', () => {
      expect(contenedor.querySelector('.grid-topes-concurrencia')).not.toBeNull();
      expect(contenedor.textContent).toContain('Subastas en las que participas');
      expect(contenedor.textContent).toContain('Pujas tuyas que van ganando');
      // Verificamos que las barras de progreso estén presentes
      const barrasProgreso = contenedor.querySelectorAll('.tope-barra-progreso');
      expect(barrasProgreso.length).toBe(2);
    });

    test('detecta y alerta el sobre-compromiso de pujas automáticas cuando superan el saldo total', () => {
      // Con las subastas iniciales, la suma de topes excede el saldo de 6200
      const alerta = contenedor.querySelector('.alerta-sobrecompromiso');
      expect(alerta).not.toBeNull();
      expect(alerta.textContent).toContain('Tus automáticas prometen más de lo que tienes');
      expect(alerta.textContent).toContain('Las últimas en responder fallarán');
    });

    test('ordena las subastas por proximidad de vencimiento y asigna bordes de estado', () => {
      const filas = contenedor.querySelectorAll('.fila-mi-subasta');
      expect(filas.length).toBe(SUBASTAS_INICIALES.length);

      // La primera debe ser la que tiene menor tiempo restante
      const tiempoPrimero = filas[0].querySelector('.reloj-fila');
      expect(tiempoPrimero).not.toBeNull();

      // Debe haber al menos una con borde verde (ganando) y una con borde rojo (superada)
      expect(contenedor.querySelector('.borde-ganando')).not.toBeNull();
      expect(contenedor.querySelector('.borde-superada')).not.toBeNull();
    });

    test('muestra botón "Ir ahora" con latido para subastas < 10 s y "Recuperarla" para superadas', () => {
      // Subasta con <= 10 s
      const btnIrAhora = contenedor.querySelector('.btn-ir-ahora');
      expect(btnIrAhora).not.toBeNull();
      expect(btnIrAhora.textContent).toContain('Ir ahora');
      expect(btnIrAhora.classList.contains('animacion-latido')).toBe(true);

      // Subasta superada
      const btnRecuperar = contenedor.querySelector('.btn-recuperar');
      expect(btnRecuperar).not.toBeNull();
      expect(btnRecuperar.textContent).toContain('Recuperarla');

      // Al pulsar "Recuperarla", debe abrir el detalle de esa subasta
      btnRecuperar.click();
      expect(controlador.vista).toBe('detalle');
      expect(contenedor.querySelector('.vista-detalle')).not.toBeNull();
    });
  });

  describe('Vista «Cierre Múltiple»', () => {
    beforeEach(() => {
      controlador.abrirCierreMultiple();
    });

    test('renderiza el balance financiero neto con cobrado, devuelto y libre', () => {
      expect(contenedor.querySelector('.panel-cierre-multiple')).not.toBeNull();
      expect(contenedor.querySelector('.cierre-titular').textContent).toContain('3 CERRARON');
      expect(contenedor.querySelector('.caja-neto .cifra-neto--cobrado')).not.toBeNull();
      expect(contenedor.querySelector('.caja-neto .cifra-neto--devuelto')).not.toBeNull();
      expect(contenedor.querySelector('.caja-neto--libre .cifra-neto--libre')).not.toBeNull();
    });

    test('renderiza filas para cada desenlace de cierre distinguiendo causas', () => {
      const filas = contenedor.querySelectorAll('.fila-evento-cierre');
      expect(filas.length).toBe(3);

      expect(contenedor.querySelector('.evento--adjudicada')).not.toBeNull();
      expect(contenedor.querySelector('.evento--superada-rival')).not.toBeNull();
      expect(contenedor.querySelector('.evento--superada-tope')).not.toBeNull();
      expect(contenedor.textContent).toContain('su automática respondió');
      expect(contenedor.textContent).toContain('paró en su tope');
    });

    test('renderiza consejo táctico personalizado con créditos exactos', () => {
      const cajaConsejo = contenedor.querySelector('.caja-consejo-tactico');
      expect(cajaConsejo).not.toBeNull();
      expect(cajaConsejo.textContent).toContain('se te escapó por 50 cr');
      expect(cajaConsejo.textContent).toContain('Tu tope estaba en 2.400');
      expect(cajaConsejo.textContent).toContain('cerró en 2.450');
      expect(cajaConsejo.textContent).toContain('más de margen era tuyo');
      // Sin saldo del servidor, el consejo NO afirma cuanto tenia libre.
      expect(cajaConsejo.textContent).not.toContain('libres');
    });

    test('el botón "Ver" de una subasta adjudicada la abre en modo cerrado/victoria', () => {
      const btnVer = contenedor.querySelector('.btn-ver-adjudicada');
      expect(btnVer).not.toBeNull();
      btnVer.click();
      expect(controlador.vista).toBe('detalle');
      expect(controlador.resultadoCierre).toBe('adjudicada');
      expect(contenedor.textContent).toContain('¡ES TUYA!');
      expect(contenedor.querySelector('#btn-pujar-manual').disabled).toBe(true);
      expect(contenedor.querySelector('#btn-solicitar-compra').disabled).toBe(true);
    });

    test('permite navegar desde el panel de cierre a mis subastas o al listado', () => {
      const btnMisSubastas = contenedor.querySelector('#btn-cierre-a-mis-subastas');
      btnMisSubastas.click();
      expect(controlador.vista).toBe('mis-subastas');

      controlador.abrirCierreMultiple();
      const btnExplorar = contenedor.querySelector('#btn-cierre-a-explorar');
      btnExplorar.click();
      expect(controlador.vista).toBe('explorar');
    });
  });

  describe('Sistema de Avisos Cruzados en Vivo (Toast en esquina inferior derecha)', () => {
    test('renderiza y descarta un toast flotante de aviso cruzado cuando te superan en otra subasta', () => {
      expect(contenedor.querySelector('.toast-cruzado-flotante')).toBeNull();

      // Disparar aviso cruzado
      controlador.lanzarAvisoCruzado('grebas-centinela');
      const toast = contenedor.querySelector('.toast-cruzado-flotante');
      expect(toast).not.toBeNull();
      expect(toast.textContent).toContain('¡Te superaron en otra subasta!');
      expect(toast.textContent).toContain('Grebas del Centinela Caído');

      // Descartar
      const btnDescartar = toast.querySelector('.btn-toast-descartar');
      btnDescartar.click();
      expect(controlador.avisoCruzado).toBeNull();
      expect(contenedor.querySelector('.toast-cruzado-flotante')).toBeNull();
    });

    test('el reloj del aviso cruzado se actualiza en el DOM con data-tiempo-subasta', () => {
      controlador.lanzarAvisoCruzado('hacha-obsidiana');
      const toast = contenedor.querySelector('.toast-cruzado-flotante');
      const relojToast = toast.querySelector('[data-tiempo-subasta="hacha-obsidiana"]');
      expect(relojToast).not.toBeNull();
      expect(relojToast.textContent).toBe('0:08');

      // Avanzamos el temporizador
      const sub = controlador.subastas.find((s) => s.id === 'hacha-obsidiana');
      sub.segundosRestantes = 7;
      controlador.actualizarTiemposEnDOM();
      expect(relojToast.textContent).toBe('0:07');
    });

    test('el botón "Ir" del aviso cruzado lleva al detalle de la subasta superada', () => {
      controlador.abrirExplorar();
      controlador.lanzarAvisoCruzado('hacha-obsidiana');

      const btnIr = contenedor.querySelector('.btn-toast-ir');
      expect(btnIr).not.toBeNull();
      btnIr.click();

      expect(controlador.vista).toBe('detalle');
      expect(controlador.subastaActivaId).toBe('hacha-obsidiana');
      expect(controlador.avisoCruzado).toBeNull();
      expect(contenedor.querySelector('.toast-cruzado-flotante')).toBeNull();
    });
  });
});

describe('HU-SUB-004 - Pruebas Unitarias de Cálculos Nuevos', () => {
  test('calcularSumaTopesAuto suma correctamente los topes de pujas automáticas', () => {
    const subastas = [{ autoLimite: 2000 }, { autoLimite: 1500 }, { autoLimite: 0 }];
    expect(calcularSumaTopesAuto(subastas)).toBe(3500);
  });

  test('verificarSobreCompromiso detecta sobre-compromiso correctamente', () => {
    const subastasExcedidas = [{ autoLimite: 4000 }, { autoLimite: 3000 }];
    const res1 = verificarSobreCompromiso(6000, subastasExcedidas);
    expect(res1.sobreCompromiso).toBe(true);
    expect(res1.sumaTopes).toBe(7000);
    expect(res1.faltante).toBe(1000);

    const subastasOk = [{ autoLimite: 2000 }, { autoLimite: 1500 }];
    const res2 = verificarSobreCompromiso(6000, subastasOk);
    expect(res2.sobreCompromiso).toBe(false);
    expect(res2.faltante).toBe(0);
  });

  test('calcularBalanceNetoCierre totaliza cobros, devoluciones y saldo libre', () => {
    const eventos = [
      { montoCobrado: 1350, montoDevuelto: 0 },
      { montoCobrado: 0, montoDevuelto: 880 },
      { montoCobrado: 0, montoDevuelto: 2400 },
    ];
    const balance = calcularBalanceNetoCierre(eventos, 6200, 720);
    expect(balance.cobrado).toBe(1350);
    expect(balance.devuelto).toBe(3280);
    expect(balance.neto).toBe(1930);
    expect(balance.saldoLibre).toBe(4130);
  });

  // El saldo total ya no tiene valor por defecto: sin el no se puede calcular
  // lo libre, y devolver un numero seria inventarlo.
  test('calcularBalanceNetoCierre deja el saldo libre en null si no se sabe el total', () => {
    const balance = calcularBalanceNetoCierre([{ montoCobrado: 100, montoDevuelto: 0 }]);
    expect(balance.cobrado).toBe(100);
    expect(balance.saldoLibre).toBeNull();
  });

  test('generarConsejoTactico calcula la diferencia y el margen necesario', () => {
    const evento = {
      nombre: 'Amuleto de Brasa Eterna',
      montoFinal: 2450,
      topePropio: 2400,
    };
    const consejo = generarConsejoTactico(evento, 4130);
    expect(consejo.diferencia).toBe(50);
    expect(consejo.margenRecomendado).toBe(100);
    expect(consejo.titulo).toContain('se te escapó por 50 cr');
    expect(consejo.cuerpo).toContain('tenías 4.130 libres');
  });

  test('calcularEstadoTopesConcurrencia genera alertas al superar el 80%', () => {
    // 8 de 10 subastas = 80%
    const subastas8 = Array.from({ length: 8 }, (_, i) => ({ id: `s-${i}`, ganando: false }));
    const estado1 = calcularEstadoTopesConcurrencia(subastas8, {
      maxSubastasSimultaneas: 10,
      maxPujasActivas: 50,
    });
    expect(estado1.subastas.alerta).toBe(true);
    expect(estado1.subastas.topeAlcanzado).toBe(false);
    expect(estado1.subastas.pista).toContain('Aviso de tope (80%)');

    // 10 de 10 subastas = 100%
    const subastas10 = Array.from({ length: 10 }, (_, i) => ({ id: `s-${i}`, ganando: false }));
    const estado2 = calcularEstadoTopesConcurrencia(subastas10, {
      maxSubastasSimultaneas: 10,
      maxPujasActivas: 50,
    });
    expect(estado2.subastas.topeAlcanzado).toBe(true);
    expect(estado2.subastas.pista).toContain('Has llegado al tope');
  });

  describe('Alertas accesibles en el DOM sin alert() bloqueante (Defecto C)', () => {
    let contenedor;
    let controlador;

    beforeEach(() => {
      contenedor = document.createElement('div');
      document.body.appendChild(contenedor);
      controlador = new ControladorSubastas({
        contenedor,
        subastas: SUBASTAS_INICIALES,
        heroes: HEROES_BASE,
      });
      controlador.render();
    });

    afterEach(() => {
      controlador.destruir();
      contenedor.remove();
    });

    test('rechazo de puja inválida muestra error en el contenedor accesible sin llamar alert()', () => {
      const alertaSpy = jest.spyOn(globalThis, 'alert').mockImplementation(() => {});
      controlador.abrirDetalle('hacha-obsidiana');

      // Puja por debajo del mínimo (oferta actual 1350, min 1400)
      const exito = controlador.pujar(1300);
      expect(exito).toBe(false);
      expect(alertaSpy).not.toHaveBeenCalled();

      const alerta = contenedor.querySelector('#alerta-pujas');
      expect(alerta).not.toBeNull();
      expect(alerta.getAttribute('role')).toBe('alert');
      expect(alerta.hidden).toBe(false);
      expect(alerta.textContent).toContain('La oferta debe ser de al menos');

      // Un nuevo intento válido limpia el error previo
      const exitoNuevo = controlador.pujar(1450);
      expect(exitoNuevo).toBe(true);
      expect(alerta.hidden).toBe(true);
      expect(alerta.textContent).toBe('');
      alertaSpy.mockRestore();
    });

    test('rechazo de auto-puja inválida muestra error en el contenedor accesible sin llamar alert()', () => {
      const alertaSpy = jest.spyOn(globalThis, 'alert').mockImplementation(() => {});
      controlador.abrirDetalle('hacha-obsidiana');

      // Límite menor al mínimo
      const exito = controlador.configurarAutoPuja(1000);
      expect(exito).toBe(false);
      expect(alertaSpy).not.toHaveBeenCalled();

      const alerta = contenedor.querySelector('#alerta-pujas');
      expect(alerta).not.toBeNull();
      expect(alerta.getAttribute('role')).toBe('alert');
      expect(alerta.hidden).toBe(false);
      expect(alerta.textContent).toContain('El tope de puja automática debe ser al menos');

      // Al configurar un límite válido se limpia el error
      const exitoNuevo = controlador.configurarAutoPuja(2500);
      expect(exitoNuevo).toBe(true);
      expect(alerta.hidden).toBe(true);
      expect(alerta.textContent).toBe('');
      alertaSpy.mockRestore();
    });

    test('rechazo de compra inmediata por saldo insuficiente muestra error en el DOM sin alert()', () => {
      const alertaSpy = jest.spyOn(globalThis, 'alert').mockImplementation(() => {});
      controlador.abrirDetalle('hacha-obsidiana');
      // hacha-obsidiana compraInmediata es 2800. El saldo ahora viene del
      // servidor, así que se simula un resumen con 500 cr disponibles: solo
      // con un saldo CONOCIDO y corto se puede rechazar aquí.
      controlador.resumen = { saldoDisponible: '500', creditosRetenidos: '0', subastasGanando: 0 };
      controlador.confirmandoCompra = true;

      const exito = controlador.confirmarCompraInmediata();
      expect(exito).toBe(false);
      expect(alertaSpy).not.toHaveBeenCalled();

      const alerta = contenedor.querySelector('#alerta-pujas');
      expect(alerta).not.toBeNull();
      expect(alerta.getAttribute('role')).toBe('alert');
      expect(alerta.hidden).toBe(false);
      expect(alerta.textContent).toContain('No dispones de saldo suficiente');

      // Navegar a otra vista limpia el error
      controlador.abrirExplorar();
      const alertaExplorar = contenedor.querySelector('#alerta-pujas');
      expect(alertaExplorar.hidden).toBe(true);
      expect(alertaExplorar.textContent).toBe('');
      alertaSpy.mockRestore();
    });
  });
});

describe('Accesibilidad del diálogo de compra (WCAG 2.1 AA)', () => {
  let contenedorA11y;
  let ctrlA11y;

  beforeEach(() => {
    contenedorA11y = document.createElement('div');
    document.body.appendChild(contenedorA11y);
    ctrlA11y = new ControladorSubastas({ contenedor: contenedorA11y });
    ctrlA11y.iniciar();
    ctrlA11y.abrirDetalle('hacha-obsidiana');
  });

  afterEach(() => {
    ctrlA11y.destruir();
    contenedorA11y.remove();
  });

  /**
   * El marcado declaraba aria-modal="true" sin implementarlo. En una pantalla
   * donde el siguiente botón gasta créditos, que el foco se quede detrás del
   * overlay significa poder confirmar una compra sin haber llegado a oír de qué.
   */
  test('al abrirse, el foco entra en el diálogo', () => {
    ctrlA11y.solicitarCompraInmediata();

    const modal = contenedorA11y.querySelector('#modal-compra-inmediata');
    expect(modal).not.toBeNull();
    expect(modal.contains(document.activeElement)).toBe(true);
  });

  test('el foco NO arranca en el botón que gasta el dinero', () => {
    ctrlA11y.solicitarCompraInmediata();

    // Abrir un diálogo con el foco puesto en "Confirmar" invita a aceptarlo
    // sin leer. Arranca en Cancelar.
    expect(document.activeElement.id).toBe('btn-cancelar-compra');
  });

  test('Escape cierra el diálogo', () => {
    ctrlA11y.solicitarCompraInmediata();
    const modal = contenedorA11y.querySelector('#modal-compra-inmediata');

    modal.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(ctrlA11y.confirmandoCompra).toBe(false);
    expect(contenedorA11y.querySelector('#modal-compra-inmediata')).toBeNull();
  });

  test('el tabulador no se escapa del diálogo', () => {
    ctrlA11y.solicitarCompraInmediata();
    const modal = contenedorA11y.querySelector('#modal-compra-inmediata');
    const botones = modal.querySelectorAll('button');
    const ultimo = botones[botones.length - 1];

    ultimo.focus();
    modal.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));

    // Vuelve al primero en vez de irse al fondo, que no está inerte.
    expect(document.activeElement).toBe(botones[0]);
  });

  test('el campo del tope automático tiene etiqueta, no solo placeholder', () => {
    // El campo solo se pinta cuando NO hay un tope puesto; la subasta de
    // ejemplo viene con uno, así que se desactiva primero.
    ctrlA11y.desactivarAutoPuja();

    const etiqueta = contenedorA11y.querySelector('label[for="input-limite-auto"]');

    // Un placeholder desaparece al escribir y no sirve como nombre accesible.
    expect(etiqueta).not.toBeNull();
    expect(etiqueta.textContent).toContain('Tope de puja automática');
  });
});

/**
 * UX-R2.8c — el marcado que llegaba del servidor.
 *
 * Esta vista se pinta con plantillas de cadena y `innerHTML`, y no escapaba
 * NADA. El nombre del objeto, su descripcion, el apodo del vendedor y el del
 * pujador salen del servidor y los escribe otra persona, asi que bastaba con
 * publicar una subasta con marcado en el nombre para ejecutar codigo en la
 * pantalla de quien la mirara. En la pantalla que mueve créditos.
 */
describe('UX-R2.8c - datos del servidor no pueden inyectar marcado', () => {
  const CARGA = '<img src=x onerror="globalThis.__colado = true">';

  /** Una subasta con la forma que espera la vista. */
  function subastaCon(campos) {
    return {
      ...SUBASTAS_INICIALES[0],
      id: 'envenenada',
      ...campos,
    };
  }

  function pintar(subasta, extra = {}) {
    const contenedor = document.createElement('div');
    document.body.appendChild(contenedor);
    const controlador = new ControladorSubastas({
      contenedor,
      subastas: [subasta],
      heroes: HEROES_BASE,
      ...extra,
    });
    controlador.render();
    return { contenedor, controlador };
  }

  afterEach(() => {
    delete globalThis.__colado;
    document.body.innerHTML = '';
  });

  test('el nombre de la subasta se lee como texto, no se ejecuta', () => {
    const { contenedor, controlador } = pintar(subastaCon({ nombre: CARGA }));

    expect(contenedor.querySelector('img')).toBeNull();
    expect(globalThis.__colado).toBeUndefined();
    expect(contenedor.textContent).toContain(CARGA);

    controlador.destruir();
  });

  test('la descripcion tampoco', () => {
    const { contenedor, controlador } = pintar(subastaCon({ descripcion: CARGA }));

    expect(contenedor.querySelector('img')).toBeNull();
    expect(globalThis.__colado).toBeUndefined();

    controlador.destruir();
  });

  test('una comilla en el nombre no abre un atributo en la barra de saldo', () => {
    // El vector concreto: `title="${t.titulo}"`, donde `t.titulo` lleva el
    // nombre de la subasta.
    const { contenedor, controlador } = pintar(
      subastaCon({ nombre: '" onmouseover="globalThis.__colado = true', retenido: 500 }),
    );
    controlador.vista = 'mis-subastas';
    controlador.render();

    const conManejador = [...contenedor.querySelectorAll('*')].filter((el) =>
      el.getAttribute('onmouseover'),
    );
    expect(conManejador).toEqual([]);

    controlador.destruir();
  });

  test('el apodo de quien puja, en el historial del detalle, tampoco', () => {
    const { contenedor, controlador } = pintar(
      subastaCon({
        historial: [{ apodo: CARGA, monto: 100, hace: '1 m', esTu: false }],
      }),
    );
    controlador.abrirDetalle('envenenada');
    controlador.render();

    expect(contenedor.querySelector('img')).toBeNull();
    expect(globalThis.__colado).toBeUndefined();

    controlador.destruir();
  });

  test('el mensaje de error del servidor tampoco', () => {
    const { contenedor, controlador } = pintar(subastaCon({}));
    controlador.estadoDatos = 'error';
    controlador.mensajeError = CARGA;
    controlador.render();

    expect(contenedor.querySelector('img')).toBeNull();
    expect(globalThis.__colado).toBeUndefined();

    controlador.destruir();
  });
});

/**
 * UX-R2.8c — «Reintentar» no reintentaba.
 */
describe('UX-R2.8c - un servicio caido no se presenta como mercado vacío', () => {
  test('Reintentar vuelve a pedir los datos, no solo repinta', async () => {
    const contenedor = document.createElement('div');
    document.body.appendChild(contenedor);

    const listar = jest.fn().mockResolvedValue([]);
    const controlador = new ControladorSubastas({
      contenedor,
      subastas: [],
      heroes: HEROES_BASE,
      api: { listar, miResumen: jest.fn().mockResolvedValue(null) },
    });
    controlador.estadoDatos = 'error';
    controlador.mensajeError = 'No se pudo contactar al servidor de subastas.';
    controlador.render();

    contenedor.querySelector('#btn-reintentar').click();

    // Antes esto era 0: el boton ponia el estado en «exito» y repintaba, y
    // como `this.subastas` estaba vacio salia «No hay subastas en curso».
    expect(listar).toHaveBeenCalledTimes(1);

    await Promise.resolve();
    controlador.destruir();
    contenedor.remove();
  });

  test('el título del error habla del servicio, no del catálogo', () => {
    const contenedor = document.createElement('div');
    document.body.appendChild(contenedor);
    const controlador = new ControladorSubastas({ contenedor, subastas: [], heroes: HEROES_BASE });
    controlador.estadoDatos = 'error';
    controlador.mensajeError = 'No se pudo contactar al servidor de subastas.';
    controlador.render();

    expect(contenedor.textContent).toContain('El mercado no responde');
    expect(contenedor.textContent).not.toContain('No hay subastas en curso');

    controlador.destruir();
    contenedor.remove();
  });
});
