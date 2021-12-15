
import {switchMap} from 'rxjs/operators';
import { Component, OnInit } from '@angular/core';
import {EventService, Event} from "../../../shared/event/event.service";
import {ActivatedRoute, Params} from "@angular/router";
import "rxjs/add/operator/switchMap";
import {PrinterService, Printer, PrinterWithUsers} from "../../printer/printer.service";
import {User, UserService} from "../../user/user.service";
import {forkJoin} from "rxjs";

@Component({
  selector: 'app-event-configuration',
  templateUrl: './event-configuration.component.html',
  styleUrls: ['./event-configuration.component.css']
})
export class EventConfigurationComponent implements OnInit {

  constructor(private eventService: EventService,
              private route: ActivatedRoute,
              private printerService: PrinterService,
              private userService: UserService) { }

  event: Event;
  printers: Array<PrinterWithUsers> = [];
  private allPrinters: Array<Printer> = [];
  private allUsers: Array<User> = [];

  ngOnInit(): void {
    this.route.params.pipe(
      switchMap((params: Params) => {
        let eventKey = params['eventKey'];
        return this.eventService.getSingleEvent(eventKey);
      }),switchMap((event: Event) => {
        this.event = event;
        return forkJoin([this.printerService.loadPrintersAndUsers(), this.printerService.loadAllPrinters(), this.userService.getUsers()]);
      }),).subscribe((data: Array<any>) => {
        this.printers = <Array<PrinterWithUsers>>data[0];
        this.allPrinters = (<Array<Printer>>data[1]).filter(p => p.active);
        this.allUsers = <Array<User>>data[2];
      });
  }

  getNotConfiguredPrinters(): Array<Printer> {
    return this.allPrinters.filter(p => !this.isPrinterDefined(p));
  }

  private isPrinterDefined(printer: Printer) {
    return this.printers.find(pu => pu.printer.id == printer.id);
  }

  getConfiguredPrinters(): Array<Printer> {
    return this.allPrinters.filter(p => this.isPrinterDefined(p));
  }

  addPrinter(printer: Printer): void {
    this.printers.push(new PrinterWithUsers(printer, []));
  }

  getNotActiveUsers(): Array<User> {
    let users = Array<User>();
    this.printers.forEach(pu => users.concat(pu.users));
    return this.allUsers.filter(u => users.findIndex(u2 => u2.id == u.id));
  }

  linkUserToPrinter(user: User, printer: Printer): void {
  }

}
