import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {Observable} from "rxjs";

@Injectable()
export class EventService {

  constructor(private http: HttpClient) { }

  getAllEvents(): Observable<Array<Event>> {
    return this.http.get<Array<Event>>('/api/internal/events');
  }

  getSingleEvent(eventKey: string): Observable<Event> {
    return this.http.get<Event>(`/api/internal/events/${eventKey}`);
  }

  toggleActivation(eventKey: string, value: boolean): Observable<boolean> {
    let url = `/api/internal/events/${eventKey}/active`;
    let call = value ? this.http.put<boolean>(url, true) : this.http.delete<boolean>(url);
    return call;
  }

}

export class Event {
  constructor(public key: string,
              public name: string,
              public imageUrl: string,
              public begin: string,
              public end: string,
              public location: string,
              public active: boolean,
              public lastUpdate: string,
              public timezone: string) {}
}
