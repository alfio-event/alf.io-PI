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
/// <reference path="../EventSource.d.ts"/>
import {Injectable, OnInit} from "@angular/core";
import {Observable, Subject} from "rxjs";
import {WindowRef} from "./window.service";
import {RSA_X931_PADDING} from "constants";

@Injectable()
export class ServerEventsService {

  private eventSource: Subject<ServerEvent>;
  events: Observable<ServerEvent>;

  constructor(private windowRef: WindowRef) {
    let $window = windowRef.nativeWindow;
    this.events = Observable.webSocket(`ws://${$window.location.host}/api/internal/ws/stream`).asObservable();
    //this.events = this.eventSource.asObservable();
    // let es = new EventSource("/api/internal/sse/stream");
    // es.addEventListener('message', data => {
    //   if(data instanceof ServerEvent) {
    //     this.eventSource.next(<ServerEvent>data);
    //   } else {
    //     console.log("unrecognized data", data);
    //   }
    // });
  }
}

export class ServerEvent {
  constructor(public type: string, public data: any) {}
}

export type EventType = "NEW_SCAN" | "EVENT_UPDATED";

export const EventType = {
  NEW_SCAN: "NEW_SCAN" as EventType,
  EVENT_UPDATED: "EVENT_UPDATED" as EventType
};

export class EventUpdated {
  constructor(public key: string, public timestamp: string) {}
}

