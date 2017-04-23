import { Component, OnInit } from '@angular/core';
import {Event, EventService} from "../../shared/event/event.service";
import {ScanService} from "../../scan-module/scan/scan.service";
import {Account} from "../../scan-module/account/account";
import {isDefined} from "@ng-bootstrap/ng-bootstrap/util/util";
import {CheckInStatus, statusDescriptions, Ticket} from "../../scan-module/scan/scan-common";
import {ProgressManager} from "../../ProgressManager";

@Component({
  selector: 'alfio-check-in',
  templateUrl: './check-in.component.html',
  styleUrls: ['./check-in.component.css']
})
export class CheckInComponent implements OnInit {

  events: Array<Event>;
  activeEvent: Event;
  account: Account;
  status: CheckInStatus;
  ticket: Ticket;
  progressManager: ProgressManager;
  loading: boolean;

  constructor(private eventService: EventService, private scanService: ScanService) {
    this.account = new Account();
    this.account.url = '';
    this.progressManager = new ProgressManager();
    this.progressManager.observable.subscribe(status => this.loading = status)
  }

  ngOnInit(): void {
    this.progressManager.monitorCall(() => this.eventService.getAllEvents().map(l => l.filter(e => e.active)))
      .subscribe(list => this.events = list);
  }

  onScan(scan: string): void {
    if(isDefined(this.activeEvent)) {
      this.progressManager.monitorCall(() => this.scanService.checkIn(this.activeEvent.key, this.account, scan))
        .subscribe(result => {
          this.status = result.result.status;
          this.ticket = result.ticket;
        }, error => {
          this.status = CheckInStatus.ERROR;
          this.ticket = null;
        });
    }
  }

  isStatusSuccess(): boolean {
    return isDefined(this.status) && this.status == CheckInStatus.SUCCESS;
  }

  isStatusError(): boolean {
    return isDefined(this.status) && this.status != CheckInStatus.SUCCESS;//missing on site payment, as per https://github.com/exteso/alf.io-PI/issues/2
  }

  getStatusMessage(): string {
    return statusDescriptions[this.status];
  }

  nextScan(): void {
    this.status = null;
    this.ticket = null;
  }
}
