/* tslint:disable:no-unused-variable */
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';

import { ScanLogComponent } from './scan-log.component';

describe('ScanLogComponent', () => {
  let component: ScanLogComponent;
  let fixture: ComponentFixture<ScanLogComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ScanLogComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ScanLogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
