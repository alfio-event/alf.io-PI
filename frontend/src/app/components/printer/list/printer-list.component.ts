import { Component, OnInit } from '@angular/core';
import {PrinterService, Printer, PrinterWithUsers} from "../printer.service";
import {ProgressManager} from "../../../ProgressManager";

@Component({
  selector: 'printer-list',
  templateUrl: './printer-list.component.html',
  styleUrls: ['./printer-list.component.css']
})
export class PrinterListComponent implements OnInit {

  printersWithUsers: Array<PrinterWithUsers> = [];
  progressManager: ProgressManager = new ProgressManager();

  constructor(private printerService: PrinterService) {
  }

  ngOnInit(): void {
    this.reloadPrinterWithUsers();
  }

  private reloadPrinterWithUsers(): void {
    this.progressManager.monitorCall(() => this.printerService.loadPrintersAndUsers())
      .subscribe(printersWithUsers => this.printersWithUsers = printersWithUsers)
  }

  toggleActivation(printer: Printer): void {
    this.progressManager.monitorCall(() => this.printerService.toggleActivation(printer.id, !printer.active))
      .subscribe(result => this.reloadPrinterWithUsers());
  }

  printTestPage(printer: Printer): void {
    this.progressManager.monitorCall(() => this.printerService.printTestPage(printer.id))
      .subscribe(result => {})
  }

}
