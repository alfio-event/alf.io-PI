import {Component, Input, OnInit} from "@angular/core";
import {ScanLogEntry, ScanLogService} from "./scan-log.service";
import {ProgressManager} from "../../ProgressManager";
import {Observable} from "rxjs";
import {Event, EventService} from "../../shared/event/event.service";
import "rxjs/add/operator/map";
import {Printer, PrinterService} from "../printer/printer.service";
import {EventType, ServerEventsService} from "../../server-events.service";

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
  currentPage = 1;
  pageSize = 3;
  found = 0;

  constructor(private scanLogService: ScanLogService,
              private eventService: EventService,
              private printerService: PrinterService,
              private serverEventsService: ServerEventsService) {
  }

  ngOnInit() {

    if(this.maxEntries != null) {
      this.pageSize = this.maxEntries;
    }

    this.loadData();
    this.serverEventsService.events.subscribe(e => {
      if(e.type == EventType.NEW_SCAN) {
        this.loadData()
      }
    })
  }

  private loadData() {
    this.progressManager
      .monitorCall(() => {
        return Observable.forkJoin(this.scanLogService.getEntries(this.currentPage - 1, this.pageSize, this.term),
          this.eventService.getAllEvents(),
          this.printerService.loadAllPrinters()
        );
      })
      .map(res => {
        let [entries, events, printers] = res;
        this.printers = printers.filter(p => p.active);
        this.found = entries.found;
        return entries.values.map(entry => new ScanLogEntryWithEvent(entry, events.find(e => e.id === entry.eventId)))
      })
      .subscribe(entries => {
        this.entries = entries
      });
  }

  reprint(entry: ScanLogEntry, printer: Printer): void {
    this.progressManager.monitorCall(() => this.scanLogService.reprint(entry.id, null, printer))
      .subscribe(res => console.log("printed", res));
  }

  changePage(newPage: number) {
    this.currentPage = newPage;
    this.loadData();
  }

  search(): void {
    this.loadData();
  }
}

export class ScanLogEntryWithEvent {
  constructor(public entry: ScanLogEntry, public event: Event) {}
}
