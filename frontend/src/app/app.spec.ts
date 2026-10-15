import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { mensajeDeError } from './core/api.service';
import { etiqueta } from './core/modelos';

describe('App', () => {
  it('se crea', async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] }).compileComponents();
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });
});

describe('etiqueta', () => {
  it('convierte los valores del backend en texto legible', () => {
    expect(etiqueta('SIN_CUANTIA')).toBe('Sin cuantía');
    expect(etiqueta('ACTIVO')).toBe('Activo');
    expect(etiqueta('ADMISION')).toBe('Admisión');
    expect(etiqueta(undefined)).toBe('');
  });
});

describe('mensajeDeError', () => {
  it('usa el mensaje del servicio', () => {
    const e = new HttpErrorResponse({ status: 409, error: { error: 'El radicado ya está registrado' } });
    expect(mensajeDeError(e)).toBe('El radicado ya está registrado');
  });

  it('une los errores de validación por campo', () => {
    const e = new HttpErrorResponse({ status: 400, error: { error: 'x', campos: { a: 'Falta a', b: 'Falta b' } } });
    expect(mensajeDeError(e)).toBe('Falta a · Falta b');
  });

  it('avisa cuando no hay conexión', () => {
    expect(mensajeDeError(new HttpErrorResponse({ status: 0 }))).toBe('No hay conexión con el servidor.');
  });
});
