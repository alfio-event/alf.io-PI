import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { UserPrinterComponent } from './user-printer.component';

describe('UserPrinterComponent', () => {
  let component: UserPrinterComponent;
  let fixture: ComponentFixture<UserPrinterComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ UserPrinterComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(UserPrinterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
