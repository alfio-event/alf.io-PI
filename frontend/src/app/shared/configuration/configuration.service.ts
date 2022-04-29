
import {map} from 'rxjs/operators';
import {HttpClient, HttpResponse} from '@angular/common/http';
import { Injectable } from '@angular/core';
import {Observable} from "rxjs";

@Injectable()
export class ConfigurationService {

  constructor(private http: HttpClient) { }


  getConfiguration(key: string) : Observable<string> {
    return this.http.get<string>('/api/internal/system/configuration/'+key, { observe: "response"})
      .pipe(map(res => getBodyOrNull(res)));
  }

  getPrinterName() : Observable<string> {
    return this.http.get<string>('/api/internal/system/printer', { observe: "response"})
      .pipe(map(res => getBodyOrNull(res)));
  }

  save(key: string, value: string) : Observable<any> {
    return this.http.post('/api/internal/system/configuration/'+key, value);
  }

  getRemainingLabels() : Observable<string> {
    return this.http.get<string>('/api/internal/system/labels/remaining', { observe: "response"})
      .pipe(map(res => getBodyOrNull(res)));
  }

  saveRemainingLabels(value: string) : Observable<any> {
    return this.http.post('/api/internal/system/labels/remaining', value);
  }

}

export const PRINTER_REMAINING_LABEL_DEFAULT_COUNTER = 'PRINTER_REMAINING_LABEL_DEFAULT_COUNTER';

function getBodyOrNull(res: HttpResponse<string>): string | null {
  if (res.ok) {
    const body = res.body;
    return body == null || body.length == 0 ? null : body
  }
  return null;
}
