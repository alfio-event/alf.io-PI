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

import {Injectable} from "@angular/core";
import {Http} from "@angular/http";
import {Observable} from "rxjs";
import {Printer} from "../printer/printer.service";
import {CheckInStatus} from "../../scan-module/scan/scan-common";

@Injectable()
export class ScanLogService {

  constructor(private http: Http) { }

  getReprintPreview(entryId: string, eventKey: string): Observable<ConfigurableLabelContent> {
    return this.http.get(`/api/internal/scan-log/event/${eventKey}/entry/${entryId}/reprint-preview`)
      .map(res => res.json())
  }

  getEntries(page: number, pageSize: number, term: string): Observable<PaginatedResult> {
    let opt = {params: {page : page, pageSize : pageSize, search : term}};
    return this.http.get(`/api/internal/scan-log`, opt)
      .map(res => res.json())
  }

  reprint(entryId: string, content: ConfigurableLabelContent, printer?: Printer): Observable<boolean> {
    let printerId = printer ? printer.id : null;
    return this.http.put(`/api/internal/scan-log/${entryId}/reprint`, {printer: printerId, content: content})
      .map(res => res.json());
  }

}

export class PaginatedResult {
  constructor(public page: number,
              public values: Array<ScanLogWithCategoryClass>,
              public found: number) {
  }
}

export class ScanLogEntry {
  constructor(public id: string,
              public eventKey: string,
              public timestamp: string,
              public ticketUuid: string,
              public userId: number,
              public localResult: CheckInStatus,
              public remoteResult: CheckInStatus,
              public badgePrinted: boolean,
              public ticket: Ticket) {
  }
}

export class ScanLogWithCategoryClass {
  constructor(public scanLog: ScanLogEntry, public boxColorClass: string) {}
}

export class Ticket {
  constructor(public firstName: string,
              public lastName: string,
              public email: string,
              public company?: string) {}
}

export class ConfigurableLabelContent {
  constructor(public firstRow: string,
              public secondRow: string,
              public additionalRows: Array<string>,
              public qrContent: string,
              public partialID: string,
              public pin: string,
              public checkbox: boolean = false) {}
}
