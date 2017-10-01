import { Component, OnInit } from '@angular/core';
import {EventService, Event} from "../../../shared/event/event.service";
import {ServerEventsService, EventType, EventUpdated} from "../../../server-events.service";

@Component({
  selector: 'event-list',
  templateUrl: './event-list.component.html',
  styleUrls: ['./event-list.component.css']
})
export class EventListComponent implements OnInit {

  events: Array<Event>;

  constructor(private eventService: EventService, private serverEventsService: ServerEventsService) { }

  ngOnInit(): void {
    this.loadEvents();
    this.serverEventsService.events.subscribe(e => {
      if(e.type == EventType.EVENT_UPDATED) {
        let {key, timestamp} = <EventUpdated>e.data;
        this.events.filter(e => e.key == key).forEach(e => e.lastUpdate = timestamp);
      }
    })
  }

  private loadEvents() {
    this.eventService.getAllEvents()
      .subscribe(events => this.events = events)
  }

  toggleActivation(event: Event): void {
    this.eventService.toggleActivation(event.key, !event.active)
      .subscribe(result => this.loadEvents());
  }

}
