import {Directive, ElementRef, EventEmitter, Input, OnInit, Optional, Output} from '@angular/core';
import {NgModel} from "@angular/forms";

@Directive({
  selector: '[virtualKeyboard]'
})
export class VirtualKeyboardDirective implements OnInit {

  ngOnInit(): void {
    let _a = this.keyboardChange;
    let ngModel = this.ngModel;
    let el = this.el;
    jQuery(el.nativeElement).keyboard({type:'', layout: 'international', accepted: (event, keyboard) => {
      if(ngModel) {
        ngModel.control.setValue(el.nativeElement.value)
      }
      if(_a.observers.length > 0) {
        _a.emit(el.nativeElement.value);
      }
    }});
  }

  @Output()
  keyboardChange: EventEmitter<String> = new EventEmitter();

  constructor(private el: ElementRef, @Optional() private ngModel: NgModel) {
  }
}
