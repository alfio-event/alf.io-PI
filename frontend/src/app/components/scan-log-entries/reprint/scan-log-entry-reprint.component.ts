/*
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */


import {Component, Input, OnInit} from "@angular/core";
import {ProgressManager} from "../../../ProgressManager";
import {Printer, PrinterService} from "../../printer/printer.service";
import {ConfigurableLabelContent, ScanLogEntry, ScanLogService} from "../scan-log.service";
import {EventService, Event} from "../../../shared/event/event.service";
import {Observable} from "rxjs/Observable";
import {ActivatedRoute, Params} from "@angular/router";

@Component({
  selector: 'scan-log-entry-reprint',
  templateUrl: './scan-log-entry-reprint.component.html'
})
export class ScanLogEntryReprintComponent implements OnInit {

  entryId: number;
  eventId: number;

  progressManager = new ProgressManager();
  content: ConfigurableLabelContent;
  printers: Array<Printer> = [];
  event: Event;

  constructor(private route: ActivatedRoute,
              private scanLogService: ScanLogService,
              private eventService: EventService,
              private printerService: PrinterService) {
  }

  ngOnInit() {
    this.route.params
      .subscribe((params: Params) => {
        this.entryId = params['entryId'];
        this.eventId = params['eventId'];
        this.loadData();
      });
  }

  private loadData() {
    this.progressManager
      .monitorCall(() => {
        return Observable.forkJoin(this.scanLogService.getReprintPreview(this.entryId, this.eventId),
          this.printerService.loadAllPrinters(),
          this.eventService.getSingleEvent(this.eventId)
        );
      })
      .subscribe(res => {
        let [content, printers, event] = res;
        this.printers = printers.filter(p => p.active);
        this.content = content;
        this.event = event;
      });
  }

  reprint(printer?: Printer): void {
    this.progressManager.monitorCall(() => this.scanLogService.reprint(this.entryId, this.content, printer))
      .subscribe(res => console.log("printed", res));
  }

}


