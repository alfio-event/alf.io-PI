import { Injectable } from '@angular/core';
import {Observable} from "rxjs";
import {User} from "../user/user.service";
import {HttpClient} from "@angular/common/http";

@Injectable()
export class PrinterService {

  constructor(private http: HttpClient) { }

  loadAllPrinters(): Observable<Array<Printer>> {
    return this.http.get<Array<Printer>>('/api/internal/printers/');
  }

  loadPrintersAndUsers(): Observable<Array<PrinterWithUsers>> {
    return this.http.get<Array<PrinterWithUsers>>(`/api/internal/printers/with-users`);
  }

  toggleActivation(printerId: number, value: boolean): Observable<boolean> {
    let url = `/api/internal/printers/${printerId}/active`;
    return value ? this.http.put<boolean>(url, true) : this.http.delete<boolean>(url);
  }

  addUserToPrinter(userId: number, printerId: number): Observable<boolean> {
    return this.http.post<boolean>(`/api/internal/user-printer/`, {userId: userId, printerId: printerId});
  }

  removeUserFromPrinters(userId: number): Observable<boolean> {
    return this.http.delete<boolean>(`/api/internal/user-printer/${userId}`);
  }

  printTestPage(printerId: number): Observable<boolean> {
    return this.http.put<boolean>(`/api/internal/printers/${printerId}/test`, {});
  }

}

export class Printer {
  constructor(public id: number, public name: string, public description: string, public active: boolean) {}
}

export class PrinterWithUsers {
  constructor(public printer: Printer, public users: Array<User>) {}
}
