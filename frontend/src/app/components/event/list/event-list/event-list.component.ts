import { Component, OnInit } from '@angular/core';
import {EventService, Event} from "../../event.service";

@Component({
  selector: 'event-list',
  templateUrl: './event-list.component.html',
  styleUrls: ['./event-list.component.css']
})
export class EventListComponent implements OnInit {

  events: Array<Event>;

  constructor(private eventService: EventService) { }

  ngOnInit(): void {
    this.eventService.getAllEvents()
      .subscribe(events => this.events = events)
  }

}