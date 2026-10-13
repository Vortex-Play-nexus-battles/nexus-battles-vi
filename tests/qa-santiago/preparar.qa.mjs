// Prepara las cuentas de la campana QA: registra y verifica (por Mailpit) las
// cuentas, y deja impreso su uid. El rol se ajusta despues por SQL en el banco.
import { request } from '@playwright/test';
import { sesionDe } from '../e2e/ayudantes/cuentas.js';

const BORDE = process.env.E2E_BORDE ?? 'http://localhost:8099';
const apodos = process.argv.slice(2);
const api = await request.newContext({ baseURL: BORDE });
for (const apodo of apodos) {
  const s = await sesionDe(api, apodo);
  console.log(`${apodo} uid=${s.claims.uid} rol=${s.claims.rol ?? s.claims.roles}`);
}
await api.dispose();
