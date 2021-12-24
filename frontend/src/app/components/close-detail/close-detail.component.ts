import { Component } from '@angular/core';
import {Router} from "@angular/router";

@Component({
  selector: 'close-detail',
  template: `<button type="button" class="close" aria-label="Close" (click)="goHome()">
                <span aria-hidden="true">&times;</span>
            </button>`,
  styleUrls: ['./close-detail.component.css']
})
export class CloseDetailComponent {

  constructor(private router: Router) { }

  goHome(): void {
    this.router.navigate(['/']);
  }

}
