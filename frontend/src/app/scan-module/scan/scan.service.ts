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
        return this.http.post(url, {"code": scan}).map(r => r.json());
    }
}
