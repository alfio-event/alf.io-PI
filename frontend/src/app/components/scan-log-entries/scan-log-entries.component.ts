import {Component, OnInit, Input} from "@angular/core";
import {ScanLogService, ScanLogEntry, Ticket, CheckInStatus} from "./scan-log.service";
import {ProgressManager} from "../../ProgressManager";
import {Observable} from "rxjs";
import {EventService, Event} from "../../shared/event/event.service";
import "rxjs/add/operator/map";
import {Printer, PrinterService} from "../printer/printer.service";
import {ServerEventsService, EventType, NewScan} from "../../server-events.service";
import {isNullOrUndefined} from "util";

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
  printers: Array<Printer> = [];

  constructor(private scanLogService: ScanLogService,
              private eventService: EventService,
              private printerService: PrinterService,
              private serverEventsService: ServerEventsService) {
  }

  ngOnInit() {
    this.loadData();
    this.serverEventsService.events.subscribe(e => {
      if(e.type == EventType.NEW_SCAN) {
        let newScan = <NewScan>e.data;
        if(!isNullOrUndefined(this.maxEntries) && this.maxEntries > 0 && this.entries.length >= this.maxEntries) {
          this.entries.splice(this.maxEntries - 1, 1);
        }
        this.entries.unshift(new ScanLogEntryWithEvent(newScan.scanData, newScan.event));
      }
    })
  }

  private loadData() {
    this.progressManager
      .monitorCall(() => {
        return Observable.forkJoin(this.scanLogService.getEntries(this.maxEntries),
          this.eventService.getAllEvents(),
          this.printerService.loadAllPrinters()
        );
      })
      .map(res => {
        let [entries, events, printers] = res;
        this.printers = printers.filter(p => p.active);
        return entries.map(entry => new ScanLogEntryWithEvent(entry, events.find(e => e.id === entry.eventId)))
      })
      .subscribe(entries => {
        this.entries = entries
      });
  }

  reprint(entry: ScanLogEntry, printer: Printer): void {
    this.progressManager.monitorCall(() => this.scanLogService.reprint(entry.id, null, printer))
      .subscribe(res => console.log("printed", res));
  }

}

export class ScanLogEntryWithEvent {
  constructor(public entry: ScanLogEntry, public event: Event) {}
}
