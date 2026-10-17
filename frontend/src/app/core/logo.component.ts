import { Component } from '@angular/core';

/** Logo de la firma (el mismo de gabinete.html): escudo con capitel griego y balanza. */
@Component({
  selector: 'app-logo',
  standalone: true,
  template: `
    <svg viewBox="0 0 120 120" role="img" aria-label="Logo de Rodríguez & Asociados">
      <g fill="#F0E7D5">
        <rect x="56" y="4" width="8" height="8" rx="2"/>
        <rect x="10" y="12" width="100" height="7" rx="3.5"/>
        <rect x="28" y="22" width="64" height="4" rx="2"/>
        <rect x="34" y="29" width="52" height="4" rx="2"/>
      </g>
      <g fill="none" stroke="#F0E7D5" stroke-width="4">
        <circle cx="16" cy="22" r="6"/><circle cx="104" cy="22" r="6"/>
      </g>
      <g stroke="#F0E7D5" stroke-width="1.6" fill="none">
        <path d="M16 28 7 60M16 28l9 32M104 28l-9 32M104 28l9 32"/>
      </g>
      <g fill="#F0E7D5">
        <path d="M4 60h24a12 8 0 0 1-24 0z"/><path d="M92 60h24a12 8 0 0 1-24 0z"/>
      </g>
      <path d="M32 38h56v38c0 18-14 28-28 36-14-8-28-18-28-36z" fill="none" stroke="#F0E7D5" stroke-width="4"/>
      <g stroke="#F0E7D5" stroke-width="4" stroke-linecap="round"><path d="M48 48v44M60 48v54M72 48v44"/></g>
    </svg>
  `,
  styles: [`:host { display: inline-grid; place-items: center; } svg { width: 100%; height: 100%; display: block; }`]
})
export class LogoComponent {}
