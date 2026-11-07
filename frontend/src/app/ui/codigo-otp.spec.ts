import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';

import { CodigoOtpComponent } from './codigo-otp.component';

@Component({
  standalone: true,
  imports: [FormsModule, CodigoOtpComponent],
  template: `<app-codigo-otp name="codigo" [(ngModel)]="codigo" (completo)="completos.push($event)" />`
})
class Anfitrion {
  codigo = '';
  completos: string[] = [];
}

/** Diseño Lexora: el código de verificación en casillas se comporta como un solo campo. */
describe('CodigoOtpComponent', () => {
  async function montar() {
    await TestBed.configureTestingModule({ imports: [Anfitrion] }).compileComponents();
    const f = TestBed.createComponent(Anfitrion);
    f.detectChanges();
    await f.whenStable();
    const casillas = Array.from(f.nativeElement.querySelectorAll('input')) as HTMLInputElement[];
    return { f, casillas };
  }

  function escribir(c: HTMLInputElement, valor: string) {
    c.value = valor;
    c.dispatchEvent(new Event('input'));
  }

  it('tiene 6 casillas con nombre accesible dentro de un grupo', async () => {
    const { f, casillas } = await montar();
    expect(casillas.length).toBe(6);
    expect(casillas[0].getAttribute('aria-label')).toBe('Dígito 1 de 6');
    expect(f.nativeElement.querySelector('[role="group"]')).not.toBeNull();
  });

  it('arma el código dígito a dígito, ignora letras y avisa al completarlo', async () => {
    const { f, casillas } = await montar();
    escribir(casillas[0], 'a');
    expect(f.componentInstance.codigo).toBe('');
    '123456'.split('').forEach((d, i) => escribir(casillas[i], d));
    f.detectChanges();
    expect(f.componentInstance.codigo).toBe('123456');
    expect(f.componentInstance.completos).toEqual(['123456']);
  });

  it('acepta pegar el código completo', async () => {
    const { f, casillas } = await montar();
    const pegado = new Event('paste', { cancelable: true }) as ClipboardEvent;
    Object.defineProperty(pegado, 'clipboardData', { value: { getData: () => '98 76 54' } });
    casillas[0].dispatchEvent(pegado);
    f.detectChanges();
    expect(f.componentInstance.codigo).toBe('987654');
    expect(f.componentInstance.completos).toEqual(['987654']);
  });

  it('muestra el valor que llega por ngModel', async () => {
    const { f } = await montar();
    f.componentInstance.codigo = '4321';
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    const valores = (Array.from(f.nativeElement.querySelectorAll('input')) as HTMLInputElement[]).map(c => c.value);
    expect(valores).toEqual(['4', '3', '2', '1', '', '']);
  });
});
