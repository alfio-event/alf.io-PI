import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfirmPowerOffComponent } from './confirm-power-off.component';

describe('ConfirmPowerOffComponent', () => {
  let component: ConfirmPowerOffComponent;
  let fixture: ComponentFixture<ConfirmPowerOffComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ConfirmPowerOffComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfirmPowerOffComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
