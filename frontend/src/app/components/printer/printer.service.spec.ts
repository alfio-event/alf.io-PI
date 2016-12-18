/* tslint:disable:no-unused-variable */

import { TestBed, async, inject } from '@angular/core/testing';
import { PrinterService } from './printer.service';

describe('PrinterService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PrinterService]
    });
  });

  it('should ...', inject([PrinterService], (service: PrinterService) => {
    expect(service).toBeTruthy();
  }));
});
