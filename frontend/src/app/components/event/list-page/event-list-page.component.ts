import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'alfio-event-list-page',
  template: `<alfio-responsive-layout>
      <event-list class="w-100 mt-4"></event-list>
    </alfio-responsive-layout>`
})
export class EventListPageComponent implements OnInit {

  constructor() { }

  ngOnInit() {
  }

}
