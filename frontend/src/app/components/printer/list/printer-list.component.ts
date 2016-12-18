import { Component, OnInit } from '@angular/core';
import {PrinterService, Printer} from "../printer.service";

@Component({
  selector: 'printer-list',
  templateUrl: './printer-list.component.html',
  styleUrls: ['./printer-list.component.css']
})
export class PrinterListComponent implements OnInit {

  printers: Array<Printer>;

  constructor(private printerService: PrinterService) { }

  ngOnInit(): void {
    this.printerService.loadAllPrinters()
      .subscribe(printers => this.printers = printers);
  }

  toggleActivation(printer: Printer): void {
    this.printerService.toggleActivation(printer.id, !printer.active)
      .subscribe(result => console.log(`printer activation result: ${result}`));
  }

}
