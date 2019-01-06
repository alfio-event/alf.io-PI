import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {Subscription, timer} from "rxjs";
import { DateTime } from 'luxon';

@Component({
  selector: 'alfio-sidebar-watch',
  template: `
    <div class="mt-2">
      <h4>{{currentTime}}</h4>
      <small>{{currentDate}}</small>
    </div>`
})
export class SidebarWatchComponent implements OnInit, OnDestroy {

  currentTime: string;
  currentDate: string;

  @Input()
  timezone: string;
  private subscription: Subscription;

  constructor() { }

  ngOnInit() {
    this.subscription = timer(100, 10000)
      .subscribe(t => {
          let dateTime = DateTime.local().setZone(this.timezone);
          this.currentTime = dateTime.toFormat('H:mm');
          this.currentDate = dateTime.toFormat('yyyy-MM-dd');
      })
  }

  ngOnDestroy(): void {
    if(this.subscription != null) {
      this.subscription.unsubscribe();
    }
  }

}
