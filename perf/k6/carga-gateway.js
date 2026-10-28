// F16: prueba de carga del gateway con k6 (docs/09-pruebas-carga.md).
// 50 usuarios concurrentes leyendo procesos, términos y clientes, la meta de docs/04-evaluacion-iso25010.md.
//
// Se inicia sesión UNA vez en setup() y todos los usuarios virtuales comparten el token: 50 logins seguidos
// dispararían el límite por IP de identidad (por el SNAT de Tailscale todos llegan con la misma IP).
// Las credenciales llegan por variables de entorno (K6_USUARIO, K6_CLAVE): nunca se escriben aquí.
// Usar una cuenta interna sin verificación en dos pasos (DEPENDIENTE o ADMINISTRATIVO).
import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8091';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '30s', target: 50 },
    { duration: '2m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],            // menos de 1 % de errores
    http_req_duration: ['p(95)<800'],          // el 95 % responde en menos de 800 ms
    'http_req_duration{pantalla:procesos}': ['p(95)<800'],
    'http_req_duration{pantalla:terminos}': ['p(95)<800'],
    'http_req_duration{pantalla:clientes}': ['p(95)<800'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'max'],
};

export function setup() {
  if (!__ENV.K6_USUARIO || !__ENV.K6_CLAVE) fail('Faltan K6_USUARIO y K6_CLAVE');
  const r = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ usuario: __ENV.K6_USUARIO, clave: __ENV.K6_CLAVE }),
    { headers: { 'Content-Type': 'application/json' } });
  if (r.status !== 200 || !r.json('token')) fail(`El login respondió ${r.status}; ¿la cuenta pide el segundo paso?`);
  return { token: r.json('token') };
}

export default function (datos) {
  const params = (pantalla) => ({ headers: { Authorization: `Bearer ${datos.token}` }, tags: { pantalla } });
  group('pantallas de consulta', () => {
    for (const [pantalla, ruta] of [['procesos', '/api/procesos'], ['terminos', '/api/terminos'], ['clientes', '/api/clientes']]) {
      const r = http.get(`${BASE}${ruta}`, params(pantalla));
      check(r, { [`${pantalla} 200`]: (x) => x.status === 200 });
    }
  });
  sleep(1 + Math.random() * 2);   // tiempo de lectura de una persona entre pantallas
}
