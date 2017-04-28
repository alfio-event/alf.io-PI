import { Component, OnInit } from '@angular/core';
import {Http} from "@angular/http";

@Component({
  selector: 'alfio-confirm-power-off',
  templateUrl: './confirm-power-off.component.html',
  styleUrls: ['./confirm-power-off.component.css']
})
export class ConfirmPowerOffComponent implements OnInit {

  message: string;

  constructor(private http: Http) { }

  ngOnInit() {
  }

  powerOff(): void {
    this.http.put('/api/internal/system/power-off', {})
      .subscribe(v => {
        this.message = v.text();
      }, e => {this.message = e})
  }

}
