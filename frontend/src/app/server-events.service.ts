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
import {Observable} from "rxjs";
import {webSocket} from "rxjs/webSocket";
import {WindowRef} from "./window.service";
import {ScanLogEntry} from "./components/scan-log-entries/scan-log.service";
import {Event} from "./shared/event/event.service";

@Injectable()
export class ServerEventsService {

  events: Observable<ServerEvent>;

  constructor(private windowRef: WindowRef) {
    let $window = windowRef.nativeWindow;
    let wsProtocol = $window.location.protocol.includes('https') ? 'wss' : 'ws';
    
    this.events = webSocket(`${wsProtocol}://${$window.location.host}/api/internal/ws/stream`).asObservable() as Observable<ServerEvent>;
  }
}

export class ServerEvent {
  constructor(public type: string, public data: any) {}
}

export type EventType = "NEW_SCAN" | "EVENT_UPDATED" | "UPDATE_PRINTER_REMAINING_LABEL_COUNTER";

export const EventType = {
  NEW_SCAN: "NEW_SCAN" as EventType,
  EVENT_UPDATED: "EVENT_UPDATED" as EventType,
  UPDATE_PRINTER_REMAINING_LABEL_COUNTER: "UPDATE_PRINTER_REMAINING_LABEL_COUNTER" as EventType
};

export class EventUpdated {
  constructor(public key: string, public timestamp: string) {}
}

export class NewScan {
  constructor(public scanData: ScanLogEntry[], public event: Event) {}
}

export class UpdatePrinterRemainingLabelCounter {
  constructor(public count: number) {}
}

