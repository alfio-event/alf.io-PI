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

@Injectable()
export class ScanLogService {

  constructor(private http: Http) { }

  getReprintPreview(entryId: number, eventId: number): Observable<ConfigurableLabelContent> {
    return this.http.get(`/api/internal/scan-log/event/${eventId}/entry/${entryId}/reprint-preview`)
      .map(res => res.json())
  }

  getEntries(page: number, pageSize: number, term: string): Observable<PaginatedResult> {
    let opt = {params: {page : page, pageSize : pageSize, search : term}};
    return this.http.get(`/api/internal/scan-log`, opt)
      .map(res => res.json())
  }

  reprint(entryId: number, content: ConfigurableLabelContent, printer?: Printer): Observable<boolean> {
    let printerId = printer ? printer.id : null;
    return this.http.put(`/api/internal/scan-log/${entryId}/reprint`, {printer: printerId, content: content})
      .map(res => res.json());
  }

}

export class PaginatedResult {
  constructor(public page: number,
              public values: Array<ScanLogEntry>,
              public found: number) {
  }
}

export class ScanLogEntry {
  constructor(public id: number,
              public eventId: number,
              public timestamp: string,
              public ticketUuid: string,
              public userId: number,
              public localResult: CheckInStatus,
              public remoteResult: CheckInStatus,
              public badgePrinted: boolean,
              public ticket: Ticket) {
  }
}

export class Ticket {
  constructor(public firstName: string,
              public lastName: string,
              public email: string,
              public company?: string) {}
}

export class ConfigurableLabelContent {
  constructor(public firstRow: String,
              public secondRow: String,
              public thirdRow: String,
              public qrContent: String,
              public partialID: String) {}
}


export type CheckInStatus = "RETRY" | "EVENT_NOT_FOUND" | "TICKET_NOT_FOUND" | "EMPTY_TICKET_CODE"
  | "INVALID_TICKET_CODE" | "INVALID_TICKET_STATE" | "ALREADY_CHECK_IN" | "MUST_PAY" | "OK_READY_TO_BE_CHECKED_IN" | "SUCCESS";

export const CheckInStatus = {
  SUCCESS: "SUCCESS" as CheckInStatus,
  MUST_PAY: "MUST_PAY" as CheckInStatus
};
