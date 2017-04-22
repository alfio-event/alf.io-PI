import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ResponsiveLayoutComponent } from './responsive-layout.component';

describe('ResponsiveLayoutComponent', () => {
  let component: ResponsiveLayoutComponent;
  let fixture: ComponentFixture<ResponsiveLayoutComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ResponsiveLayoutComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ResponsiveLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
