import {Component, Input, OnInit} from '@angular/core';
import {Observable} from "rxjs";
import {Event} from "../../shared/event/event.service";

@Component({
  selector: 'alfio-responsive-layout',
  templateUrl: './responsive-layout.component.html',
  styleUrls: ['./responsive-layout.component.css']
})
export class ResponsiveLayoutComponent implements OnInit {

  @Input()
  eventSelectionListener: Observable<Event>;

  constructor() { }

  ngOnInit() {
  }

}
