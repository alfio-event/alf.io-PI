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

  getEntry(entryId: number): Observable<ScanLogEntry> {
    return this.http.get(`/api/internal/scan-log/${entryId}`)
      .map(res => res.json())
  }

  getEntries(max: number): Observable<Array<ScanLogEntry>> {
    return this.http.get(`/api/internal/scan-log?max=${max || -1}`)
      .map(res => res.json())
  }

  reprint(entry: ScanLogEntry, printer: Printer): Observable<boolean> {
    return this.http.get(`/api/internal/scan-log/${entry.id}/reprint?printer=${printer.id}`)
      .map(res => res.json());
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

  get localSuccess(): boolean {
    return this.localResult == CheckInStatus.SUCCESS;
  }

  get remoteSuccess(): boolean {
    return this.remoteResult == CheckInStatus.SUCCESS;
  }
}

export class Ticket {
  constructor(public firstName: string,
              public lastName: string,
              public email: string,
              public company?: string) {}
}


export enum CheckInStatus {
  RETRY,
  EVENT_NOT_FOUND,
  TICKET_NOT_FOUND,
  EMPTY_TICKET_CODE,
  INVALID_TICKET_CODE,
  INVALID_TICKET_STATE,
  ALREADY_CHECK_IN,
  MUST_PAY,
  OK_READY_TO_BE_CHECKED_IN,
  SUCCESS
}
