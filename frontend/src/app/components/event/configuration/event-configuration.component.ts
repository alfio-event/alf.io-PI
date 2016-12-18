import { Component, OnInit } from '@angular/core';
import {EventService} from "../event.service";
import {ActivatedRoute, Params} from "@angular/router";
import "rxjs/add/operator/switchMap";

@Component({
  selector: 'app-event-configuration',
  templateUrl: './event-configuration.component.html',
  styleUrls: ['./event-configuration.component.css']
})
export class EventConfigurationComponent implements OnInit {

  constructor(private eventService: EventService, private route: ActivatedRoute) { }

  event: Event;

  ngOnInit(): void {
    this.route.params
      .switchMap((params: Params) => {
        let eventId = params['eventId'];
        return this.eventService.getSingleEvent(eventId);
      }).subscribe(event => this.event = event);
  }

}
