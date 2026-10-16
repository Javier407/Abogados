import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { Perito } from '../../core/modelos';

/** Peritos para las pruebas periciales: especialidad, tarifa del dictamen y calificación. */
@Component({
  selector: 'app-peritos',
  standalone: true,
  imports: [FormsModule, CurrencyPipe],
  templateUrl: './peritos.component.html'
})
export class PeritosComponent implements OnInit {
  private api = inject(ApiService);

  peritos: Perito[] = [];
  perito: Perito = this.vacio();
  editandoId: number | null = null;
  busqueda = '';
  enviado = false;
  mensaje = '';
  error = '';

  ngOnInit(): void { this.cargar(); }

  vacio(): Perito {
    return { nombre: '', especialidad: '', profesion: '', registro: '', telefono: '', email: '', ciudad: '',
      tarifaDictamen: null, calificacion: null };
  }

  cargar(): void {
    this.api.peritos(this.busqueda).subscribe({ next: p => this.peritos = p, error: e => this.error = mensajeDeError(e) });
  }

  estrellas(n?: number | null): string {
    return n ? '★'.repeat(n) + '☆'.repeat(5 - n) : '—';
  }

  guardar(form: NgForm): void {
    this.enviado = true;
    this.mensaje = this.error = '';
    if (form.invalid) return;
    const peticion = this.editandoId ? this.api.actualizarPerito(this.editandoId, this.perito) : this.api.crearPerito(this.perito);
    peticion.subscribe({
      next: p => {
        this.mensaje = `Perito ${p.nombre} ${this.editandoId ? 'actualizado' : 'registrado'}.`;
        this.cancelar(form);
        this.cargar();
      },
      error: e => this.error = mensajeDeError(e)
    });
  }

  editar(p: Perito): void {
    this.editandoId = p.idPerito!;
    this.perito = { ...p };
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelar(form: NgForm): void {
    this.editandoId = null;
    this.enviado = false;
    form.resetForm(this.vacio());
    this.perito = this.vacio();
  }
}
