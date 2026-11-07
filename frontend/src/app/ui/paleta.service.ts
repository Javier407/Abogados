import { Injectable, signal } from '@angular/core';

/** Abre y cierra la paleta de búsqueda (Ctrl+K) desde cualquier pantalla. */
@Injectable({ providedIn: 'root' })
export class PaletaService {
  readonly abierta = signal(false);
  abrir(): void { this.abierta.set(true); }
  cerrar(): void { this.abierta.set(false); }
  alternar(): void { this.abierta.update(v => !v); }
}
