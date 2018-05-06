import { Injectable } from '@angular/core';
import {Http} from "@angular/http";
import {Observable} from "rxjs/Observable";

@Injectable()
export class ConfigurationService {

  constructor(private http: Http) { }


  getConfiguration(key: string) : Observable<string> {
    return this.http.get('/api/internal/system/configuration/'+key).map(res => res.text().length == 0 ? null : res.json())
  }

  getPrinterName() : Observable<string> {
    return this.http.get('/api/internal/system/printer').map(res => res.text().length == 0 ? null : res.text())
  }

  save(key: string, value: string) : Observable<any> {
    return this.http.post('/api/internal/system/configuration/'+key, value);
  }

  getRemainingLabels() : Observable<string> {
    return this.http.get('/api/internal/system/labels/remaining').map(res => res.text().length == 0 ? null : res.text())
  }

  saveRemainingLabels(value: string) : Observable<any> {
    return this.http.post('/api/internal/system/labels/remaining', value);
  }

}

export const PRINTER_REMAINING_LABEL_DEFAULT_COUNTER = 'PRINTER_REMAINING_LABEL_DEFAULT_COUNTER';
