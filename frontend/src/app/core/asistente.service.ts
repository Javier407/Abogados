import { Injectable, inject } from '@angular/core';

import { ApiService } from './api.service';
import { EventoBorrador, TipoBorrador } from './modelos';

/**
 * Pide un borrador al asistente y entrega el texto a medida que el modelo lo escribe.
 * El servicio responde Server-Sent Events a un POST; EventSource solo hace GET, por eso se lee con fetch.
 */
@Injectable({ providedIn: 'root' })
export class AsistenteService {
  private api = inject(ApiService);

  async redactar(idProceso: number, tipo: TipoBorrador, instrucciones: string,
                 alRecibir: (e: EventoBorrador) => void, cancelar?: AbortSignal): Promise<void> {
    const r = await fetch(this.api.urlBorradores(), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ idProceso, tipo, instrucciones }),
      signal: cancelar
    });
    if (!r.ok || !r.body) {
      let mensaje = `El asistente respondió ${r.status}`;
      try { mensaje = (await r.json()).error ?? mensaje; } catch { /* sin cuerpo JSON */ }
      throw new Error(mensaje);
    }
    const lector = r.body.getReader();
    const decodificador = new TextDecoder();
    let pendiente = '';
    for (;;) {
      const { value, done } = await lector.read();
      if (done) break;
      pendiente += decodificador.decode(value, { stream: true });
      // Cada evento termina con una línea en blanco; su dato va en la línea "data: {...}".
      let corte: number;
      while ((corte = pendiente.indexOf('\n\n')) >= 0) {
        const bloque = pendiente.slice(0, corte);
        pendiente = pendiente.slice(corte + 2);
        const datos = bloque.split('\n').filter(l => l.startsWith('data: ')).map(l => l.slice(6)).join('\n');
        if (datos) alRecibir(JSON.parse(datos) as EventoBorrador);
      }
    }
  }
}
