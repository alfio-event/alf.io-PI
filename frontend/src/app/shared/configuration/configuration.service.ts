
import {map} from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {Observable} from "rxjs";

@Injectable()
export class ConfigurationService {

  constructor(private http: HttpClient) { }


  getConfiguration(key: string) : Observable<string> {
    return this.http.get<string>('/api/internal/system/configuration/'+key).pipe(map(res => res.length == 0 ? null : res))
  }

  getPrinterName() : Observable<string> {
    return this.http.get<string>('/api/internal/system/printer').pipe(map(res => res.length == 0 ? null : res))
  }

  save(key: string, value: string) : Observable<any> {
    return this.http.post('/api/internal/system/configuration/'+key, value);
  }

  getRemainingLabels() : Observable<string> {
    return this.http.get<string>('/api/internal/system/labels/remaining').pipe(map(res => res.length == 0 ? null : res))
  }

  saveRemainingLabels(value: string) : Observable<any> {
    return this.http.post('/api/internal/system/labels/remaining', value);
  }

}

export const PRINTER_REMAINING_LABEL_DEFAULT_COUNTER = 'PRINTER_REMAINING_LABEL_DEFAULT_COUNTER';
