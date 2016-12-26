import { Injectable } from '@angular/core';
import {Http} from "@angular/http";
import {Observable} from "rxjs";

@Injectable()
export class EventService {

  constructor(private http: Http) { }

  getAllEvents(): Observable<Array<Event>> {
    return this.http.get('/api/internal/events')
      .map(res => res.json())
  }

  getSingleEvent(eventId: number): Observable<Event> {
    return this.http.get(`/api/internal/events/${eventId}`)
      .map(res => res.json())
  }

  toggleActivation(eventId: number, value: boolean): Observable<boolean> {
    let url = `/api/internal/events/${eventId}/active`;
    let call = value ? this.http.put(url, true) : this.http.delete(url);
    return call.map(res => res.json());
  }

  addUserToPrinter(eventId: number, userId: number, printerId: number): Observable<boolean> {
    return this.http.post(`/api/internal/events/${eventId}/user-printer/`, {userId: userId, printerId: printerId})
      .map(res => res.json())
  }

}

export class Event {
  constructor(public id: number,
              public key: string,
              public name: string,
              public imageUrl: string,
              public begin: string,
              public end: string,
              public location: string,
              public active: boolean) {}
}
