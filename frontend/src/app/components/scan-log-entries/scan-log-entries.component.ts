import {Component, OnInit, Input} from "@angular/core";
import {ScanLogService, ScanLogEntry, CheckInStatus, Ticket} from "./scan-log.service";
import {ProgressManager} from "../../ProgressManager";
import {Observable} from "rxjs";
import {EventService, Event} from "../event/event.service";
import "rxjs/add/operator/map";

@Component({
  selector: 'scan-log-entries',
  templateUrl: './scan-log-entries.component.html'
})
export class ScanLogEntriesComponent implements OnInit {

  @Input()
  maxEntries?: number;
  @Input()
  enableFilter: boolean;

  progressManager = new ProgressManager();
  entries: Array<ScanLogEntryWithEvent> = [];
  term: string;

  constructor(private scanLogService: ScanLogService, private eventService: EventService) {
  }

  ngOnInit() {
    this.progressManager
      .monitorCall(() => Observable.forkJoin(this.scanLogService.getEntries(this.maxEntries), this.eventService.getAllEvents()))
      .map(res => {
        let entries = res[0];
        let events = res[1];
        return entries.map(entry => new ScanLogEntryWithEvent(entry, events.find(e => e.id === entry.eventId)))
      })
      .subscribe(entries => {
        this.entries = entries
      });
  }

}

export class ScanLogEntryWithEvent {
  constructor(public entry: ScanLogEntry, public event: Event) {}
}
