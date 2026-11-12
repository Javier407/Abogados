import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AvisoCookiesComponent } from './legal/aviso-cookies.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AvisoCookiesComponent],
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.css',
})
export class App {}
