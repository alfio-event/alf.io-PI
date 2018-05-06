import { Component, OnInit } from '@angular/core';
import {ConfigurationService, PRINTER_REMAINING_LABEL_DEFAULT_COUNTER} from "../../shared/configuration/configuration.service";
import {ProgressManager} from "../../ProgressManager";

@Component({
  selector: 'alfio-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {

  labelCounter: any = '';
  defaultLabelCounter: any = '';
  printerName: any;
  progressManager: ProgressManager;
  loading: boolean;

  constructor(private configurationService: ConfigurationService) {
    this.progressManager = new ProgressManager();
    this.progressManager.observable.subscribe(status => this.loading = status)
  }


  ngOnInit() {
    this.progressManager
      .monitorCall(() => this.configurationService.getRemainingLabels())
      .subscribe(res => this.labelCounter = res);

    this.progressManager
      .monitorCall(() => this.configurationService.getConfiguration(PRINTER_REMAINING_LABEL_DEFAULT_COUNTER))
      .subscribe(res => this.defaultLabelCounter = res);

    this.progressManager
      .monitorCall(() => this.configurationService.getPrinterName())
      .subscribe(res => {
        this.printerName = res
      });


  }

  saveLabel() {
    this.progressManager
      .monitorCall(() => this.configurationService.saveRemainingLabels(this.labelCounter))
      .subscribe(r => {})
  }

  saveDefaultLabel() {
    this.progressManager
      .monitorCall(() => this.configurationService.save(PRINTER_REMAINING_LABEL_DEFAULT_COUNTER, this.defaultLabelCounter))
      .subscribe(r => {})
  }
}
