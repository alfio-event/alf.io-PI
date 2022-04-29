import { Injectable } from "@angular/core";
import { Observable } from 'rxjs';
import { TicketAndCheckInResult } from './scan-common';
import { Account } from "../account/account";
import { HttpClient } from "@angular/common/http";

@Injectable()
export class ScanService {
    constructor(private http: HttpClient) {
    }

    public checkIn(eventKey: string, account: Account, scan: string): Observable<TicketAndCheckInResult> {
        let split = scan.split("/");
        return this.performCheckIn(account, `${account.url}/admin/api/check-in/event/${eventKey}/ticket/${split[0]}`, scan);
    }

    public forcePrintLabel(eventKey: string, account: Account, scan: string) {
        let split = scan.split("/");
        let url = `${account.url}/admin/api/check-in/event/${eventKey}/force-print-label-ticket/${split[0]}`;
        return this.http.post(url, {"code" : scan});
    }

    public confirmPayment(eventKey: string, account: Account, scan: string): Observable<TicketAndCheckInResult> {
        let split = scan.split("/");
        return this.performCheckIn(account, `${account.url}/admin/api/check-in/event/${eventKey}/ticket/${split[0]}/confirm-on-site-payment`, scan);
    }

    private performCheckIn(account: Account, url: string, scan: string): Observable<TicketAndCheckInResult> {
        let code = scan;
        if(scan.indexOf('/') == -1) {
          code = null;
        }
        return this.http.post<TicketAndCheckInResult>(url, {"code": code});
    }
}
