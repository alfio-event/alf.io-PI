import { Component, OnInit } from '@angular/core';
import {PrinterService, Printer, PrinterWithUsers} from "../printer.service";
import {DragulaService} from "ng2-dragula";
import {ProgressManager} from "../../../ProgressManager";

@Component({
  selector: 'printer-list',
  templateUrl: './printer-list.component.html',
  styleUrls: ['./printer-list.component.css']
})
export class PrinterListComponent implements OnInit {

  printersWithUsers: Array<PrinterWithUsers> = [];
  progressManager: ProgressManager = new ProgressManager();

  constructor(private printerService: PrinterService,
              private dragulaService: DragulaService) {
    this.dragulaService.drop.subscribe(e => {
      let userElement = <Node & ChildNode>e[1];
      let printerElement = <Node>e[2];
      if(userElement != null && printerElement != null) {
        let userId = +userElement.attributes.getNamedItem('user-id').value;
        let printerId = +printerElement.attributes.getNamedItem('printer-id').value;
        this.progressManager.monitorCall(() => this.printerService.addUserToPrinter(userId, printerId))
          .subscribe(res => {
            if(res) {
              this.reloadPrinterWithUsers();
            }
          });
        userElement.remove();
      } else if(userElement != null && userElement.attributes.getNamedItem('printer-id') != null) {
        let userId = +userElement.attributes.getNamedItem('user-id').value;
        this.progressManager.monitorCall(() => this.printerService.removeUserFromPrinters(userId))
          .subscribe(res => {
            if(res) {
              this.reloadPrinterWithUsers();
            }
          });
      }
    });
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
