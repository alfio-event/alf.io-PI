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


import {Component, OnInit} from "@angular/core";
import {ProgressManager} from "../../../ProgressManager";
import {Printer, PrinterService} from "../../printer/printer.service";
import {ConfigurableLabelContent, ScanLogService} from "../scan-log.service";
import {EventService, Event} from "../../../shared/event/event.service";
import {ActivatedRoute, Params} from "@angular/router";
import { forkJoin } from "rxjs";

@Component({
  selector: 'scan-log-entry-reprint',
  templateUrl: './scan-log-entry-reprint.component.html'
})
export class ScanLogEntryReprintComponent implements OnInit {

  entryId: string;
  eventKey: string;

  progressManager = new ProgressManager();
  content: PreviewContent;
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
        this.eventKey = params['eventKey'];
        this.loadData();
      });
  }

  private loadData() {
    this.progressManager
      .monitorCall(() => {
        return forkJoin([this.scanLogService.getReprintPreview(this.entryId, this.eventKey),
          this.printerService.loadAllPrinters(),
          this.eventService.getSingleEvent(this.eventKey)
        ]);
      })
      .subscribe(res => {
        let [content, printers, event] = res;
        this.printers = printers.filter(p => p.active);
        this.content = PreviewContent.fromConfigurableLabelContent(content);
        this.event = event;
      });
  }

  reprint(printer?: Printer): void {
    this.progressManager.monitorCall(() => this.scanLogService.reprint(this.entryId, this.content.toConfigurableLabelContent(), printer))
      .subscribe(res => console.log("printed", res));
  }

}

export class PreviewContent {
  constructor(public firstRow: string,
              public secondRow: string,
              public additionalRows: Array<{value: string}>,
              public qrContent: string,
              public partialID: string,
              public pin: string,
              public checkbox: boolean) {}

  static fromConfigurableLabelContent(content: ConfigurableLabelContent): PreviewContent {
    return new PreviewContent(content.firstRow, content.secondRow, content.additionalRows.map(v => ({value: v})), content.qrContent, content.partialID, content.pin, content.checkbox);
  }

  public toConfigurableLabelContent(): ConfigurableLabelContent {
    return new ConfigurableLabelContent(this.firstRow, this.secondRow, this.additionalRows.map(m => m.value), this.qrContent, this.partialID, this.pin, this.checkbox);
  }
}


