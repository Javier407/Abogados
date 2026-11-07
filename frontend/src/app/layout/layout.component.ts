import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideBell, lucideBookOpenCheck, lucideBriefcase, lucideBuilding2, lucideCalendarClock, lucideChartColumn,
  lucideChevronDown, lucideChevronRight, lucideEye, lucideFlaskConical, lucideFolderOpen, lucideHandCoins, lucideKeyRound,
  lucideLayoutDashboard, lucideLogOut, lucideMenu, lucideMessagesSquare, lucideMoon, lucidePanelLeftClose,
  lucidePanelLeftOpen, lucideReceipt, lucideSearch, lucideServer, lucideSun, lucideUser, lucideUsers, lucideWallet
} from '@ng-icons/lucide';
import { HlmDropdownMenuImports } from '@spartan-ng/helm/dropdown-menu';

import { AuthService } from '../core/auth.service';
import { SesionService } from '../core/sesion.service';
import { LogoComponent } from '../core/logo.component';
import { TemaService } from '../core/tema.service';
import { PaletaService } from '../ui/paleta.service';
import { PaletaComandosComponent } from '../ui/paleta-comandos.component';

interface Opcion {
  ruta: string;
  etiqueta: string;
  icono: string;
  /** Prefijos de URL en los que la opción se marca activa (por defecto, su ruta). */
  activoEn?: string[];
  exacta?: boolean;
}
interface Grupo { id: string; titulo: string; opciones: Opcion[]; plegable?: boolean; }

const PERSONAL_JURIDICO = ['ADMIN', 'SOCIO', 'ABOGADO', 'DEPENDIENTE'];
const CON_FINANZAS = ['ADMIN', 'SOCIO', 'ABOGADO', 'ADMINISTRATIVO'];
const NOMBRES_ROL: Record<string, string> = { ADMIN: 'Administrador', SOCIO: 'Socio', ABOGADO: 'Abogado',
  DEPENDIENTE: 'Dependiente', ADMINISTRATIVO: 'Administrativo', CLIENTE: 'Cliente' };

/**
 * Marco de la aplicación: menú lateral agrupado por tipo de trabajo (colapsable a iconos; en el celular,
 * panel que se abre con el botón de menú) y barra superior con migas, tema, avisos y el menú del usuario.
 */
@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, LogoComponent, NgIcon, HlmDropdownMenuImports, PaletaComandosComponent],
  providers: [provideIcons({
    lucideBell, lucideBookOpenCheck, lucideBriefcase, lucideBuilding2, lucideCalendarClock, lucideChartColumn,
    lucideChevronDown, lucideChevronRight, lucideEye, lucideFlaskConical, lucideFolderOpen, lucideHandCoins, lucideKeyRound,
    lucideLayoutDashboard, lucideLogOut, lucideMenu, lucideMessagesSquare, lucideMoon, lucidePanelLeftClose,
    lucidePanelLeftOpen, lucideReceipt, lucideSearch, lucideServer, lucideSun, lucideUser, lucideUsers, lucideWallet
  })],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.css'
})
export class LayoutComponent {
  readonly auth = inject(AuthService);
  readonly sesion = inject(SesionService);
  readonly tema = inject(TemaService);
  readonly paleta = inject(PaletaService);
  private readonly router = inject(Router);

  readonly hoy = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());

  readonly url = toSignal(this.router.events.pipe(
    filter((e): e is NavigationEnd => e instanceof NavigationEnd),
    map(e => sinParametros(e.urlAfterRedirects))
  ), { initialValue: sinParametros(this.router.url) });

  readonly colapsado = signal(leer('lexora.menu.colapsado') === '1');
  readonly menuMovil = signal(false);
  readonly abiertos = signal<Set<string>>(new Set((leer('lexora.menu.grupos') ?? 'firma').split(',').filter(Boolean)));

  readonly iniciales = computed(() => {
    const partes = (this.auth.sesion()?.nombre ?? '').split(/\s+/).filter(Boolean);
    return ((partes[0]?.[0] ?? '') + (partes[1]?.[0] ?? '')).toUpperCase();
  });

  readonly rol = computed(() => (this.auth.sesion()?.roles ?? []).map(r => NOMBRES_ROL[r] ?? r).join(' · '));

  /** Menú según el rol: lo diario arriba, lo administrativo abajo y plegable. */
  readonly grupos = computed<Grupo[]>(() => {
    const a = this.auth;
    if (a.esCliente() && !a.esPersonal()) {
      return [{ id: 'portal', titulo: 'Mi portal', opciones: [
        { ruta: '/portal', etiqueta: 'Mis procesos', icono: 'lucideFolderOpen', exacta: true, activoEn: ['/portal/procesos'] },
        { ruta: '/portal/pagos', etiqueta: 'Mis pagos', icono: 'lucideWallet' }
      ] }];
    }
    const grupos: Grupo[] = [
      { id: 'diario', titulo: 'Diario', opciones: [
        { ruta: '/', etiqueta: 'Inicio', icono: 'lucideLayoutDashboard', exacta: true },
        { ruta: '/terminos', etiqueta: 'Agenda', icono: 'lucideCalendarClock', activoEn: ['/terminos', '/calendario'] },
        { ruta: '/procesos', etiqueta: 'Procesos', icono: 'lucideFolderOpen' },
        ...(a.tiene(...PERSONAL_JURIDICO) ? [{ ruta: '/vigilancia', etiqueta: 'Vigilancia', icono: 'lucideEye' }] : [])
      ] },
      { id: 'clientes', titulo: 'Clientes', opciones: [
        { ruta: '/clientes', etiqueta: 'Clientes', icono: 'lucideUsers' },
        { ruta: '/consultas', etiqueta: 'Consultas', icono: 'lucideMessagesSquare' }
      ] }
    ];
    if (a.tiene(...CON_FINANZAS)) {
      grupos.push({ id: 'finanzas', titulo: 'Finanzas', opciones: [
        { ruta: '/honorarios', etiqueta: 'Honorarios', icono: 'lucideHandCoins' },
        { ruta: '/facturacion', etiqueta: 'Facturación', icono: 'lucideReceipt', activoEn: ['/facturacion', '/facturas'] }
      ] });
    }
    grupos.push({ id: 'firma', titulo: 'La firma', plegable: true, opciones: [
      { ruta: '/abogados', etiqueta: 'Abogados', icono: 'lucideBriefcase' },
      { ruta: '/peritos', etiqueta: 'Peritos', icono: 'lucideFlaskConical' },
      { ruta: '/reglas', etiqueta: 'Reglas de términos', icono: 'lucideBookOpenCheck' },
      ...(a.tiene('ADMIN', 'SOCIO') ? [
        { ruta: '/reportes', etiqueta: 'Reportes', icono: 'lucideChartColumn' },
        { ruta: '/firma', etiqueta: 'Datos de la firma', icono: 'lucideBuilding2' }
      ] : [])
    ] });
    if (a.tiene('ADMIN')) {
      grupos.push({ id: 'admin', titulo: 'Administración', plegable: true, opciones: [
        { ruta: '/usuarios', etiqueta: 'Usuarios', icono: 'lucideKeyRound' },
        { ruta: '/servidor', etiqueta: 'Servidor', icono: 'lucideServer' }
      ] });
    }
    return grupos;
  });

  /** Dónde está el usuario, para las migas de pan. */
  readonly ubicacion = computed(() => {
    const url = this.url();
    for (const g of this.grupos()) {
      const o = g.opciones.find(x => this.activa(x, url));
      if (o) return { grupo: g.titulo, pagina: o.etiqueta, ruta: o.ruta, detalle: !o.exacta && url !== o.ruta && url.split('/').length > 2 };
    }
    if (url.startsWith('/avisos')) return { grupo: '', pagina: 'Avisos', ruta: '/avisos', detalle: false };
    if (url.startsWith('/cuenta')) return { grupo: '', pagina: 'Mi cuenta', ruta: '/cuenta', detalle: false };
    return null;
  });

  activa(o: Opcion, url = this.url()): boolean {
    if (o.exacta && url === o.ruta) return true;
    const prefijos = o.activoEn ?? (o.exacta ? [] : [o.ruta]);
    return prefijos.some(p => url === p || url.startsWith(p + '/'));
  }

  grupoAbierto(g: Grupo): boolean {
    return !g.plegable || this.colapsado() || this.abiertos().has(g.id) || g.opciones.some(o => this.activa(o));
  }

  alternarGrupo(g: Grupo): void {
    const s = new Set(this.abiertos());
    if (s.has(g.id)) s.delete(g.id); else s.add(g.id);
    this.abiertos.set(s);
    guardar('lexora.menu.grupos', [...s].join(','));
  }

  /** Ctrl+K o ⌘K abre la búsqueda global desde cualquier pantalla. */
  @HostListener('document:keydown', ['$event'])
  atajo(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k' && this.auth.esPersonal()) {
      e.preventDefault();
      this.paleta.alternar();
    }
  }

  alternarColapso(): void {
    this.colapsado.update(v => !v);
    guardar('lexora.menu.colapsado', this.colapsado() ? '1' : '0');
  }
}

function sinParametros(url: string): string {
  return url.split(/[?#]/)[0] || '/';
}
function leer(clave: string): string | null {
  try { return localStorage.getItem(clave); } catch { return null; }
}
function guardar(clave: string, valor: string): void {
  try { localStorage.setItem(clave, valor); } catch { /* sin almacenamiento: no se recuerda */ }
}
