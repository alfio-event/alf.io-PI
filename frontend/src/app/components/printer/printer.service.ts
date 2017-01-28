import { Injectable } from '@angular/core';
import {Http, URLSearchParams} from "@angular/http";
import {Observable} from "rxjs";
import {User} from "../user/user.service";

@Injectable()
export class PrinterService {

  constructor(private http: Http) { }

  loadAllPrinters(): Observable<Array<Printer>> {
    return this.http.get('/api/internal/printers/')
      .map(res => res.json())
  }

  loadPrintersAndUsers(): Observable<Array<PrinterWithUsers>> {
    return this.http.get(`/api/internal/printers/with-users`)
      .map(res => res.json())
  }

  toggleActivation(printerId: number, value: boolean): Observable<boolean> {
    let url = `/api/internal/printers/${printerId}/active`;
    let call = value ? this.http.put(url, true) : this.http.delete(url);
    return call.map(res => res.json());
  }

  addUserToPrinter(userId: number, printerId: number): Observable<boolean> {
    return this.http.post(`/api/internal/user-printer/`, {userId: userId, printerId: printerId})
      .map(res => res.json())
  }

  removeUserFromPrinters(userId: number): Observable<boolean> {
    return this.http.delete(`/api/internal/user-printer/${userId}`)
      .map(res => res.json())
  }

  printTestPage(printerId: number): Observable<boolean> {
    return this.http.put(`/api/internal/printers/${printerId}/test`, {})
      .map(res => res.json())
  }

}

export class Printer {
  constructor(public id: number, public name: string, public description: string, public active: boolean) {}
}

export class PrinterWithUsers {
  constructor(public printer: Printer, public users: Array<User>) {}
}
