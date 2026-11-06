import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService, mensajeDeError } from '../../core/api.service';
import { AuthService, EDITAN_CLIENTES } from '../../core/auth.service';
import { Abogado, Consulta, ConsultaConAvisos, EstadoConsulta, Rama, Sede, etiqueta } from '../../core/modelos';

/**
 * Consultas iniciales (F10): quien llega a la firma antes de ser cliente. Al registrarla se avisa si la contraparte
 * ya es cliente (conflicto de intereses). Si contrata, se crea su ficha de cliente; si no, se cierra con el motivo.
 */
@Component({
  selector: 'app-consultas',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <div class="modulo-container">
      <div class="page-header">
        <h1>Consultas iniciales</h1>
        <p>Quién consultó, contra quién sería el caso y en qué terminó. Se revisa el conflicto de intereses.</p>
      </div>
      @if (mensaje) { <div class="alerta alerta-exito">{{ mensaje }}</div> }
      @if (error) { <div class="alerta alerta-error" role="alert">{{ error }}</div> }

      <div class="filtros">
        <select class="form-input" [(ngModel)]="filtro" (ngModelChange)="cargar()" aria-label="Estado de la consulta">
          <option value="">Todas</option>
          @for (e of estados; track e) { <option [value]="e">{{ etiqueta(e) }}</option> }
        </select>
        <button class="btn btn-primary" type="button" (click)="nueva = !nueva">Nueva consulta</button>
      </div>

      @if (nueva) {
        <section class="card">
          <div class="card-header"><h2>Nueva consulta</h2></div>
          <div class="card-body">
            <form #f="ngForm" (ngSubmit)="registrar(f)" novalidate>
              <div class="form-grid">
                <div class="form-group">
                  <label class="form-label" for="cNombre">Quién consulta <span class="requerido">*</span></label>
                  <input id="cNombre" class="form-input" name="nombre" [(ngModel)]="datos.nombre" required maxlength="150" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="cTipo">Documento</label>
                  <select id="cTipo" class="form-input" name="tipoDocumento" [(ngModel)]="datos.tipoDocumento">
                    <option [ngValue]="null">Sin documento</option>
                    <option value="CC">Cédula</option><option value="CE">Cédula de extranjería</option>
                    <option value="NIT">NIT</option><option value="PAS">Pasaporte</option>
                  </select>
                </div>
                <div class="form-group">
                  <label class="form-label" for="cNumero">Número</label>
                  <input id="cNumero" class="form-input" name="numeroDocumento" [(ngModel)]="datos.numeroDocumento"
                         [disabled]="!datos.tipoDocumento" maxlength="15" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="cEmail">Correo</label>
                  <input id="cEmail" class="form-input" type="email" name="email" [(ngModel)]="datos.email" maxlength="120" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="cTel">Teléfono</label>
                  <input id="cTel" class="form-input" name="telefono" [(ngModel)]="datos.telefono" maxlength="20" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="cRama">Rama</label>
                  <select id="cRama" class="form-input" name="codigoRama" [(ngModel)]="datos.codigoRama">
                    <option [ngValue]="null">Sin definir</option>
                    @for (r of ramas; track r.codigo) { <option [value]="r.codigo">{{ r.nombre }}</option> }
                  </select>
                </div>
                <div class="form-group">
                  <label class="form-label" for="cContra">Contraparte</label>
                  <input id="cContra" class="form-input" name="contraparte" [(ngModel)]="datos.contraparte" maxlength="150"
                         placeholder="Contra quién sería el caso" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="cContraDoc">Documento de la contraparte</label>
                  <input id="cContraDoc" class="form-input" name="contraparteDocumento" [(ngModel)]="datos.contraparteDocumento" maxlength="15" />
                </div>
                <div class="form-group">
                  <label class="form-label" for="cAbogado">Atiende</label>
                  <select id="cAbogado" class="form-input" name="idAbogado" [(ngModel)]="datos.idAbogado">
                    <option [ngValue]="null">Sin asignar</option>
                    @for (a of abogados; track a.idPersona) { <option [ngValue]="a.idPersona">{{ a.nombres }} {{ a.apellidos }}</option> }
                  </select>
                </div>
                <div class="form-group form-group-full">
                  <label class="form-label" for="cAsunto">Asunto <span class="requerido">*</span></label>
                  <input id="cAsunto" class="form-input" name="asunto" [(ngModel)]="datos.asunto" required maxlength="300"
                         placeholder="En una frase: qué necesita" />
                </div>
                <div class="form-group form-group-full">
                  <label class="form-label" for="cDesc">Notas de la consulta</label>
                  <textarea id="cDesc" class="form-input form-textarea" name="descripcion" [(ngModel)]="datos.descripcion" maxlength="2000"></textarea>
                </div>
              </div>
              <div class="form-actions">
                <button class="btn btn-primary" type="submit" [disabled]="guardando">Registrar</button>
                <button class="btn btn-secondary" type="button" (click)="nueva = false">Cancelar</button>
              </div>
            </form>
          </div>
        </section>
      }

      @if (seleccion; as s) {
        <section class="card">
          <div class="card-header"><h2>{{ s.consulta.nombre }} · {{ etiqueta(s.consulta.estado) }}</h2></div>
          <div class="card-body">
            @for (a of s.avisos; track a) { <div class="alerta alerta-error" role="alert">{{ a }}</div> }
            <p><strong>{{ s.consulta.asunto }}</strong></p>
            @if (s.consulta.descripcion) { <p class="portal-resumen">{{ s.consulta.descripcion }}</p> }
            <p class="form-ayuda">
              {{ s.consulta.fecha | date:'dd/MM/yyyy' }}
              @if (s.consulta.numeroDocumento) { · {{ s.consulta.tipoDocumento }} {{ s.consulta.numeroDocumento }} }
              @if (s.consulta.email) { · {{ s.consulta.email }} }
              @if (s.consulta.telefono) { · {{ s.consulta.telefono }} }
              @if (s.consulta.contraparte) { · Contraparte: {{ s.consulta.contraparte }} }
            </p>
            @if (s.consulta.motivoCierre) { <p class="form-ayuda">Motivo: {{ s.consulta.motivoCierre }}</p> }
            @if (s.consulta.idCliente) { <p><a routerLink="/clientes">Cliente {{ s.consulta.idCliente }}</a></p> }

            @if (s.consulta.estado === 'ABIERTA') {
              <div class="form-grid">
                @if (puedeContratar) {
                  <div class="form-group">
                    <label class="form-label" for="kCiudad">Ciudad del cliente</label>
                    <input id="kCiudad" class="form-input" [(ngModel)]="contrato.ciudad" maxlength="60" />
                  </div>
                  <div class="form-group">
                    <label class="form-label" for="kSede">Sede</label>
                    <select id="kSede" class="form-input" [(ngModel)]="contrato.idSede">
                      @for (se of sedes; track se.idSede) { <option [ngValue]="se.idSede">{{ se.nombre }}</option> }
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label" for="kDir">Dirección</label>
                    <input id="kDir" class="form-input" [(ngModel)]="contrato.direccion" maxlength="150" />
                  </div>
                }
                <div class="form-group form-group-full">
                  <label class="form-label" for="kMotivo">Si no contrata: motivo</label>
                  <input id="kMotivo" class="form-input" [(ngModel)]="motivo" maxlength="300"
                         placeholder="Ej.: no aceptó los honorarios; asunto penal remitido a un colega" />
                </div>
              </div>
              <div class="form-actions">
                @if (puedeContratar) {
                  <button class="btn btn-primary" type="button" (click)="contratar(s.consulta)" [disabled]="guardando">Contrató: crear cliente</button>
                }
                <button class="btn btn-secondary" type="button" (click)="cerrar(s.consulta, 'NO_CONTRATADA')" [disabled]="guardando">No contrató</button>
                <button class="btn btn-secondary" type="button" (click)="cerrar(s.consulta, 'REMITIDA')" [disabled]="guardando">Remitida</button>
              </div>
            }
          </div>
        </section>
      }

      @if (consultas.length === 0) {
        <div class="empty-state">No hay consultas registradas.</div>
      } @else {
        <div class="tabla-wrapper">
          <table class="tabla">
            <thead><tr><th>Fecha</th><th>Quién consulta</th><th>Asunto</th><th>Contraparte</th><th>Estado</th></tr></thead>
            <tbody>
              @for (c of consultas; track c.idConsulta) {
                <tr class="fila-clic" (click)="ver(c)" tabindex="0" (keydown.enter)="ver(c)">
                  <td class="fecha-celda">{{ c.fecha | date:'dd/MM/yyyy' }}</td>
                  <td class="titulo-celda">{{ c.nombre }}</td>
                  <td>{{ c.asunto }}</td>
                  <td>{{ c.contraparte || '—' }}</td>
                  <td><span class="badge" [class.badge-warning]="c.estado === 'ABIERTA'" [class.badge-success]="c.estado === 'CONTRATADA'"
                            [class.badge-muted]="c.estado !== 'ABIERTA' && c.estado !== 'CONTRATADA'">{{ etiqueta(c.estado) }}</span></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class ConsultasComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  readonly etiqueta = etiqueta;
  readonly estados: EstadoConsulta[] = ['ABIERTA', 'CONTRATADA', 'NO_CONTRATADA', 'REMITIDA'];
  readonly puedeContratar = this.auth.tiene(...EDITAN_CLIENTES);

  consultas: Consulta[] = [];
  ramas: Rama[] = [];
  sedes: Sede[] = [];
  abogados: Abogado[] = [];
  filtro: EstadoConsulta | '' = 'ABIERTA';
  nueva = false;
  datos: Partial<Consulta> = this.vacia();
  seleccion: ConsultaConAvisos | null = null;
  contrato = { ciudad: '', idSede: 1 as number | null, direccion: '' };
  motivo = '';
  guardando = false;
  mensaje = '';
  error = '';

  ngOnInit(): void {
    this.cargar();
    forkJoin({ ramas: this.api.ramas(), sedes: this.api.sedes(), abogados: this.api.abogados(true) }).subscribe({
      next: r => { this.ramas = r.ramas; this.sedes = r.sedes; this.abogados = r.abogados; },
      error: () => {}
    });
  }

  cargar(): void {
    this.api.consultas(this.filtro).subscribe({ next: c => this.consultas = c, error: e => this.error = mensajeDeError(e) });
  }

  ver(c: Consulta): void {
    this.mensaje = this.error = '';
    this.motivo = '';
    this.api.consulta(c.idConsulta).subscribe({ next: s => this.seleccion = s, error: e => this.error = mensajeDeError(e) });
  }

  registrar(f: NgForm): void {
    if (f.invalid) { this.error = 'Escriba quién consulta y el asunto.'; return; }
    this.enviar(this.api.registrarConsulta({ ...this.datos, numeroDocumento: this.datos.tipoDocumento ? this.datos.numeroDocumento : null }), r => {
      this.mensaje = r.avisos.length ? 'Consulta registrada, con avisos de conflicto de intereses.' : 'Consulta registrada.';
      this.nueva = false;
      this.datos = this.vacia();
    });
  }

  contratar(c: Consulta): void {
    if (!this.contrato.ciudad.trim()) { this.error = 'Escriba la ciudad del cliente.'; return; }
    this.enviar(this.api.contratarConsulta(c.idConsulta, this.contrato),
      r => this.mensaje = `Consulta contratada: cliente ${r.consulta.idCliente}.`);
  }

  cerrar(c: Consulta, estado: EstadoConsulta): void {
    if (!this.motivo.trim()) { this.error = 'Escriba el motivo.'; return; }
    this.enviar(this.api.cerrarConsulta(c.idConsulta, estado, this.motivo), () => this.mensaje = 'Consulta cerrada.');
  }

  private enviar(peticion: import('rxjs').Observable<ConsultaConAvisos>, alTerminar: (r: ConsultaConAvisos) => void): void {
    this.guardando = true;
    this.mensaje = this.error = '';
    peticion.subscribe({
      next: r => { this.guardando = false; this.seleccion = r; alTerminar(r); this.cargar(); },
      error: e => { this.guardando = false; this.error = mensajeDeError(e); }
    });
  }

  private vacia(): Partial<Consulta> {
    return { nombre: '', tipoDocumento: null, numeroDocumento: '', email: '', telefono: '', contraparte: '',
      contraparteDocumento: '', codigoRama: null, asunto: '', descripcion: '', idAbogado: null };
  }
}
