import { Injectable } from '@angular/core';
import {Http} from "@angular/http";
import {Observable} from "rxjs";
import {User} from "../user/user.service";

@Injectable()
export class PrinterService {

  constructor(private http: Http) { }

  loadAllPrinters(): Observable<Array<Printer>> {
    return this.http.get('/api/internal/printers/')
      .map(res => res.json())
  }

  loadPrintersForEvent(eventId: number): Observable<Array<PrinterWithUsers>> {
    return this.http.get(`/api/internal/printers/for-event/${eventId}`)
      .map(res => res.json())
  }

  toggleActivation(printerId: number, value: boolean): Observable<boolean> {
    let url = `/api/internal/printers/${printerId}/active`;
    let call = value ? this.http.put(url, true) : this.http.delete(url);
    return call.map(res => res.json());
  }

}

export class Printer {
  constructor(public id: number, public name: string, public description: string, public active: boolean) {}
}

export class PrinterWithUsers {
  constructor(public printer: Printer, public users: Array<User>) {}
}
