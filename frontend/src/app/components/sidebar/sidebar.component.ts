import {Component, Input, OnInit} from '@angular/core';
import {Event} from "../../shared/event/event.service";

@Component({
  selector: 'alfio-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.css']
})
export class SidebarComponent implements OnInit {

  @Input()
  event: Event;

  constructor() { }

  ngOnInit() {
  }

}
