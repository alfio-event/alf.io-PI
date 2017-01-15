import { Component, OnInit } from '@angular/core';
import {EventService, Event} from "../event.service";

@Component({
  selector: 'event-list',
  templateUrl: './event-list.component.html',
  styleUrls: ['./event-list.component.css']
})
export class EventListComponent implements OnInit {

  events: Array<Event>;

  constructor(private eventService: EventService) { }

  ngOnInit(): void {
    this.loadEvents();
  }

  private loadEvents() {
    this.eventService.getAllEvents()
      .subscribe(events => this.events = events)
  }

  toggleActivation(event: Event): void {
    this.eventService.toggleActivation(event.id, !event.active)
      .subscribe(result => console.log(`event activation result: ${result}`));
  }

}
