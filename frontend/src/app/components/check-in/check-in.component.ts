import { Component, OnInit } from '@angular/core';
import {Event, EventService} from "../../shared/event/event.service";
import {ScanService} from "../../scan-module/scan/scan.service";
import {Account} from "../../scan-module/account/account";
import {isDefined} from "@ng-bootstrap/ng-bootstrap/util/util";
import {CheckInStatus, statusDescriptions, Ticket} from "../../scan-module/scan/scan-common";
import {ProgressManager} from "../../ProgressManager";
import {EventType, ServerEventsService, UpdatePrinterRemainingLabelCounter} from "../../server-events.service";
import {ConfigurationService, PRINTER_REMAINING_LABEL_COUNTER} from "../../shared/configuration/configuration.service";

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
  toScan: string;

  testMode = false;

  labelCounter: any;

  constructor(private eventService: EventService,
              private scanService: ScanService,
              private serverEventsService: ServerEventsService,
              private configurationService: ConfigurationService) {
    this.account = new Account();
    this.account.url = '';
    this.progressManager = new ProgressManager();
    this.progressManager.observable.subscribe(status => this.loading = status)
  }

  ngOnInit(): void {
    this.progressManager.monitorCall(() => this.eventService.getAllEvents().map(l => l.filter(e => e.active)))
      .subscribe(list => this.events = list);

    this.serverEventsService.events.subscribe(e => {
      if(e.type == EventType.UPDATE_PRINTER_REMAINING_LABEL_COUNTER) {
        let update = <UpdatePrinterRemainingLabelCounter> e.data;
        this.labelCounter = update.count;
      }
    });

    this.configurationService.getConfiguration(PRINTER_REMAINING_LABEL_COUNTER).subscribe(res => this.labelCounter = res);
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

  setActiveEvent(event: Event) {
    this.activeEvent = event;
  }
}
