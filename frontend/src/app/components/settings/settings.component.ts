import { Component, OnInit } from '@angular/core';
import {ConfigurationService, PRINTER_REMAINING_LABEL_COUNTER} from "../../shared/configuration/configuration.service";

@Component({
  selector: 'alfio-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {

  constructor(private configurationService: ConfigurationService) { }

  labelCounter: any

  ngOnInit() {
    this.configurationService.getConfiguration(PRINTER_REMAINING_LABEL_COUNTER)
      .subscribe(res => this.labelCounter = res);
  }

  saveLabel() {
    this.configurationService.save(PRINTER_REMAINING_LABEL_COUNTER, this.labelCounter).subscribe(r => {})
  }

}
