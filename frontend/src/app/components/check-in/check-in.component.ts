import {Component, OnInit, ViewChild, OnDestroy} from '@angular/core';
import {Event, EventService} from "../../shared/event/event.service";
import {ScanService} from "../../scan-module/scan/scan.service";
import {Account} from "../../scan-module/account/account";
import {
  CheckInStatus,
  ForceBadgePrintIsAllowed,
  statusDescriptions,
  SuccessStatuses,
  Ticket
} from "../../scan-module/scan/scan-common";
import {ProgressManager} from "../../ProgressManager";
import {EventType, ServerEventsService, UpdatePrinterRemainingLabelCounter} from "../../server-events.service";
import {ConfigurationService, PRINTER_REMAINING_LABEL_DEFAULT_COUNTER} from "../../shared/configuration/configuration.service";
import {Observable, Subject, Subscription} from "rxjs";

function isDefined(value: any): boolean {
  return value !== undefined && value !== null;
}

@Component({
  selector: 'alfio-check-in',
  templateUrl: './check-in.component.html',
  styleUrls: ['./check-in.component.css']
})
export class CheckInComponent implements OnInit, OnDestroy {

  events: Array<Event>;
  activeEvent: Event;
  account: Account;
  status: CheckInStatus;
  ticket: Ticket;
  progressManager: ProgressManager;
  loading: boolean;
  toScan: string;
  boxColorClass: string;
  textColorClass: string;

  testMode = false;

  @ViewChild('keyListener') keyListener;

  eventSelectionListener: Observable<Event>;
  private eventSelectionSubject: Subject<Event>;
  private serverEventsSub: Subscription;

  labelCounter: any;
  labelDefaultCounter: any;

  constructor(private eventService: EventService,
              private scanService: ScanService,
              private serverEventsService: ServerEventsService,
              private configurationService: ConfigurationService) {
    this.account = new Account();
    this.account.url = '';
    this.progressManager = new ProgressManager();
    this.progressManager.observable.subscribe(status => this.loading = status);
    this.eventSelectionSubject = new Subject();
    this.eventSelectionListener = this.eventSelectionSubject.asObservable();
  }

  ngOnInit(): void {
    this.progressManager.monitorCall(() => this.eventService.getAllEvents().map(l => l.filter(e => e.active)))
      .subscribe(list => this.events = list);

    this.serverEventsSub = this.serverEventsService.events.subscribe(e => {
      if(e.type == EventType.UPDATE_PRINTER_REMAINING_LABEL_COUNTER) {
        let update = <UpdatePrinterRemainingLabelCounter> e.data;
        this.labelCounter = update.count;
      }
    });

    this.configurationService.getRemainingLabels().subscribe(res => this.labelCounter = res);
    this.configurationService.getConfiguration(PRINTER_REMAINING_LABEL_DEFAULT_COUNTER).subscribe(res => this.labelDefaultCounter = res);
  }

  ngOnDestroy(): void {
    this.serverEventsSub.unsubscribe();
  }

  onScan(scan: string): void {
    this.toScan = scan;
    if(isDefined(this.activeEvent)) {
      this.progressManager.monitorCall(() => this.scanService.checkIn(this.activeEvent.key, this.account, scan))
        .subscribe(result => {
          this.status = result.result.status;
          this.ticket = result.ticket;
          this.boxColorClass = result.result.boxColorClass;
          this.textColorClass = CheckInComponent.getTextColor(this.boxColorClass);
        }, error => {
          this.status = CheckInStatus.ERROR;
          this.ticket = null;
          this.boxColorClass = "danger";
        });
    }
  }

  private static getTextColor(boxColor: string): string {
    if (['faded', 'light', 'transparent', 'white'].indexOf(boxColor) > -1) {
      return 'text-dark';
    }
    return 'text-white';
  }

  forcePrint() {
    this.scanService.forcePrintLabel(this.activeEvent.key, this.account, this.toScan).subscribe(result => {})
  }

  isStatusSuccess(): boolean {
    return isDefined(this.status) && SuccessStatuses.indexOf(this.status) > -1;
  }

  isStatusWarning(): boolean {
    return isDefined(this.status) && this.status == CheckInStatus.BADGE_SCAN_ALREADY_DONE;
  }

  canPrintLabel(): boolean {
    return isDefined(this.status) && this.ticket != null && ForceBadgePrintIsAllowed.indexOf(this.status) > -1;
  }

  isStatusError(): boolean {
    return isDefined(this.status)
      && !this.isStatusSuccess()
      && !this.isStatusWarning();//missing on site payment, as per https://github.com/exteso/alf.io-PI/issues/2
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
    this.eventSelectionSubject.next(event);
  }

  confirmResetLabelCounter() {
    if(confirm('Reset label counter to ' + this.labelDefaultCounter+'?')) {
      this.configurationService.saveRemainingLabels(this.labelDefaultCounter).subscribe(() => {
        this.configurationService.getRemainingLabels().subscribe(res => this.labelCounter = res);
      })
    }

    this.keyListener.nativeElement.focus();
  }
}