import {Component, Input, OnInit, OnDestroy} from "@angular/core";
import {ScanLogEntry, ScanLogService} from "./scan-log.service";
import {ProgressManager} from "../../ProgressManager";
import {Observable, Subscription} from "rxjs";
import {Event, EventService} from "../../shared/event/event.service";
import "rxjs/add/operator/map";
import {EventType, ServerEventsService} from "../../server-events.service";

@Component({
  selector: 'scan-log-entries',
  templateUrl: './scan-log-entries.component.html'
})
export class ScanLogEntriesComponent implements OnInit, OnDestroy {


  @Input()
  maxEntries?: number;
  @Input()
  enableFilter: boolean;

  progressManager = new ProgressManager();
  entries: Array<ScanLogEntryWithEvent> = [];
  term: string;
  currentPage = 1;
  pageSize = 3;
  found = 0;

  private serverEventsSub: Subscription;

  constructor(private scanLogService: ScanLogService,
              private eventService: EventService,
              private serverEventsService: ServerEventsService) {
  }

  ngOnInit() {

    if(this.maxEntries != null) {
      this.pageSize = this.maxEntries;
    }

    this.loadData();
    this.serverEventsSub = this.serverEventsService.events.subscribe(e => {
      if(e.type == EventType.NEW_SCAN) {
        this.loadData()
      }
    })
  }

  ngOnDestroy(): void {
    this.serverEventsSub.unsubscribe();
  }

  private loadData() {
    this.progressManager
      .monitorCall(() => {
        return Observable.forkJoin(this.scanLogService.getEntries(this.currentPage - 1, this.pageSize, this.term),
          this.eventService.getAllEvents()
        );
      })
      .map(res => {
        let [entries, events] = res;
        this.found = entries.found;
        return entries.values.map(entryWithBoxClass => {
          const entry = entryWithBoxClass.scanLog;
          return new ScanLogEntryWithEvent(entry, events.find(e => e.key === entry.eventKey), entryWithBoxClass.boxColorClass);
        })
      })
      .subscribe(entries => {
        this.entries = entries
      });
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
  constructor(public entry: ScanLogEntry, public event: Event, public boxColorClass) {}
}
