import { Component, OnInit } from '@angular/core';
import {Http} from "@angular/http";

@Component({
  selector: 'alfio-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {

  constructor(private http: Http) { }

  labelCounter: any

  ngOnInit() {
    this.http.get('/api/internal/system/configuration/PRINTER_REMAINING_LABEL_COUNTER')
      .map(res => res.json())
      .subscribe(res => this.labelCounter = res);
  }

  saveLabel() {
    this.http.post('/api/internal/system/configuration/PRINTER_REMAINING_LABEL_COUNTER', this.labelCounter).subscribe(r => {})
  }

}
