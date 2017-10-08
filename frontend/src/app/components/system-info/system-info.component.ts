import { Component, OnInit } from '@angular/core';
import {Http} from "@angular/http";

@Component({
  selector: 'app-system-info',
  templateUrl: './system-info.component.html',
  styleUrls: ['./system-info.component.css']
})
export class SystemInfoComponent implements OnInit {

  constructor(private http: Http) { }

  nameInCluster: string;
  namesInCluster: string;
  ipAddresses: string;
  attendeeDataCount: number;

  ngOnInit() {
    this.http.get('/api/internal/system/cluster/me').map(res => res.text()).subscribe(r => this.nameInCluster = r);
    this.http.get('/api/internal/system/cluster/all').map(res => res.json()).subscribe(r => this.namesInCluster = r.join(', '));
    this.http.get('/api/internal/system/ip').map(res => res.json()).subscribe(r => this.ipAddresses = r.join(', '));
    this.http.get('/api/internal/system/tables/attendee/count').map(res => res.json()).subscribe(r => this.attendeeDataCount = r);
  }

}
