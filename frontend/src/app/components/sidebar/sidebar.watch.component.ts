import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {Observable, Subscription} from "rxjs";
import { DateTime } from 'luxon';

@Component({
  selector: 'alfio-sidebar-watch',
  template: `
    <div class="mt-2">
      <h4>{{currentTime.toFormat('H:mm')}}</h4>
      <small>{{currentTime.toFormat('yyyy-MM-dd')}}</small>
    </div>`
})
export class SidebarWatchComponent implements OnInit, OnDestroy {

  currentTime: DateTime;

  @Input()
  timezone: string;
  private subscription: Subscription;

  constructor() { }

  ngOnInit() {
    this.subscription = Observable.timer(100, 10000)
      .subscribe(t => {
          this.currentTime = DateTime.local().setZone(this.timezone);
      })
  }

  ngOnDestroy(): void {
    if(this.subscription != null) {
      this.subscription.unsubscribe();
    }
  }

}
