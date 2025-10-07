import { Injectable } from "@angular/core";
import { Http } from "@angular/http";
import { Observable } from 'rxjs';
import { TicketAndCheckInResult } from './scan-common';
import { Account } from "../account/account";

@Injectable()
export class ScanService {
    constructor(private http: Http) {
    }

    public checkIn(eventKey: string, account: Account, scan: string): Observable<TicketAndCheckInResult> {
        let cleanScan = this.cleanScan(scan);
        let split = cleanScan.split("/");
        return this.performCheckIn(account, `${account.url}/admin/api/check-in/event/${eventKey}/ticket/${split[0]}`, cleanScan);
    }

    public forcePrintLabel(eventKey: string, account: Account, scan: string) {
        let cleanScan = this.cleanScan(scan);
        let split = cleanScan.split("/");
        let url = `${account.url}/admin/api/check-in/event/${eventKey}/force-print-label-ticket/${split[0]}`;
        return this.http.post(url, {"code" : cleanScan});
    }

    public confirmPayment(eventKey: string, account: Account, scan: string): Observable<TicketAndCheckInResult> {
        let cleanScan = this.cleanScan(scan);
        let split = cleanScan.split("/");
        return this.performCheckIn(account, `${account.url}/admin/api/check-in/event/${eventKey}/ticket/${split[0]}/confirm-on-site-payment`, cleanScan);
    }

    private performCheckIn(account: Account, url: string, scan: string): Observable<TicketAndCheckInResult> {
        let code = scan;
        if(scan.indexOf('/') == -1) {
          code = null;
        }
        return this.http.post(url, {"code": code}).map(r => r.json());
    }

    private cleanScan(scan: string): string {
      // remove any occurrence of \000026
      const cleanScan = scan.replace(/\\000026/g,'');
      const firstSlashIndex = cleanScan.indexOf("/");
      if (firstSlashIndex === -1) {
        return cleanScan; // code is not valid
      }
      let uuid = cleanScan.substring(0, firstSlashIndex);
      const code = cleanScan.substring(firstSlashIndex+1);

      if (uuid.length > 36) {
        // code contains control chars
        uuid = uuid.substring(uuid.length - 36);
      }

      return `${uuid}/${code}`;
    }
}
