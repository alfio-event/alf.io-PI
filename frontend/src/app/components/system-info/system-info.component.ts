import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-system-info',
  templateUrl: './system-info.component.html',
  styleUrls: ['./system-info.component.css']
})
export class SystemInfoComponent implements OnInit {

  constructor(private http: HttpClient) { }

  nameInCluster: string;
  namesInCluster: string;
  ipAddresses: string;
  attendeeDataCount: number;
  isLeader:boolean;

  ngOnInit() {
    this.http.get<string>('/api/internal/system/cluster/me').subscribe(r => this.nameInCluster = r);
    this.http.get<string>('/api/internal/system/cluster/all').subscribe(r => this.namesInCluster = r);
    this.http.get<boolean>('/api/internal/system/cluster/is-leader').subscribe(r => this.isLeader = r);
    this.http.get<string[]>('/api/internal/system/ip').subscribe(r => this.ipAddresses = r.join(', '));
    this.http.get<number>('/api/internal/system/tables/attendee/count').subscribe(r => this.attendeeDataCount = r);
  }

}
