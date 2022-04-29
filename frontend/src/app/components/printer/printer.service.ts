import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {Observable} from "rxjs";

@Injectable()
export class PrinterService {

  constructor(private http: HttpClient) { }

  loadAllPrinters(): Observable<Array<Printer>> {
    return this.http.get<Array<Printer>>('/api/internal/printers/')
  }
  
  printTestPage(printerId: number): Observable<boolean> {
    return this.http.put<boolean>(`/api/internal/printers/${printerId}/test`, {});
  }

}

export class Printer {
  constructor(public id: number, public name: string, public description: string, public active: boolean) {}
}