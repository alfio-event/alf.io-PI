import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'alfio-confirm-power-off',
  templateUrl: './confirm-power-off.component.html',
  styleUrls: ['./confirm-power-off.component.css']
})
export class ConfirmPowerOffComponent implements OnInit {

  message: string;

  constructor(private http: HttpClient) { }

  ngOnInit() {
  }

  powerOff(): void {
    this.http.put<string>('/api/internal/system/power-off', {})
      .subscribe(v => {
        this.message = v;
      }, e => {this.message = e})
  }

}
