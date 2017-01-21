import {Component, OnInit, Input} from "@angular/core";
import {ScanLogService, ScanLogEntry, Ticket, CheckInStatus} from "./scan-log.service";
import {ProgressManager} from "../../ProgressManager";
import {Observable} from "rxjs";
import {EventService, Event} from "../event/event.service";
import "rxjs/add/operator/map";
import {Printer, PrinterService} from "../printer/printer.service";
import {ServerEventsService, EventType} from "../../server-events.service";

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
        console.log(e);
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
    this.progressManager.monitorCall(() => this.scanLogService.reprint(entry, printer))
  }

}

export class ScanLogEntryWithEvent {
  constructor(public entry: ScanLogEntry, public event: Event) {}
}
