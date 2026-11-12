import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AvisoCookiesComponent } from './aviso-cookies.component';
import { CookiesComponent } from './cookies.component';
import { ALMACENAMIENTO, CLAVE_AVISO_COOKIES, DATOS_LEGALES } from './datos-legales';
import { PrivacidadComponent } from './privacidad.component';

/** Textos legales: el aviso de cookies informa una vez por versión; las políticas muestran los datos del responsable. */
describe('Privacidad y cookies', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  function montar<T>(c: Type<T>) {
    const f = TestBed.createComponent(c);
    f.detectChanges();
    return f;
  }

  it('el aviso de cookies sale la primera vez, se cierra y no vuelve hasta que cambie la versión', () => {
    const f = montar(AvisoCookiesComponent);
    expect(f.nativeElement.querySelector('[aria-label="Aviso de cookies"]')).not.toBeNull();

    (f.nativeElement.querySelector('button') as HTMLButtonElement).click();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[aria-label="Aviso de cookies"]')).toBeNull();
    expect(localStorage.getItem(CLAVE_AVISO_COOKIES)).toBe(DATOS_LEGALES.version);

    expect(montar(AvisoCookiesComponent).nativeElement.querySelector('[aria-label="Aviso de cookies"]')).toBeNull();
    localStorage.setItem(CLAVE_AVISO_COOKIES, '0.9');   // versión anterior: hay que volver a avisar
    expect(montar(AvisoCookiesComponent).nativeElement.querySelector('[aria-label="Aviso de cookies"]')).not.toBeNull();
  });

  it('la política de cookies lista todo lo que la app guarda en el navegador', () => {
    const texto = montar(CookiesComponent).nativeElement.textContent as string;
    for (const c of ALMACENAMIENTO) expect(texto).toContain(c.nombre);
    expect(texto).toContain('No usamos cookies de analítica, publicidad ni seguimiento');
  });

  it('la política de privacidad identifica al responsable y los plazos de respuesta de la ley', () => {
    const texto = montar(PrivacidadComponent).nativeElement.textContent as string;
    expect(texto).toContain(DATOS_LEGALES.responsable);
    expect(texto).toContain('Ley 1581 de 2012');
    expect(texto).toContain('10 días hábiles');
    expect(texto).toContain('15 días hábiles');
    expect(texto).toContain('Superintendencia de Industria y Comercio');
    // Mientras no la revise un abogado, se avisa que es un borrador
    if (!DATOS_LEGALES.revisada) expect(texto).toContain('Borrador pendiente de revisión jurídica');
  });
});
