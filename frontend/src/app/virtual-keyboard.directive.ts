import {Directive, ElementRef, EventEmitter, Optional, Output} from '@angular/core';
import {NgModel} from "@angular/forms";

@Directive({
  selector: '[virtualKeyboard]'
})
export class VirtualKeyboardDirective {

  @Output()
  acceptedKeyboard: EventEmitter<String> = new EventEmitter();

  constructor(el: ElementRef, @Optional() ngModel: NgModel) {
    let _a = this.acceptedKeyboard;
    jQuery(el.nativeElement).keyboard({type:'', layout: 'international', accepted: (event, keyboard) => {
      _a.emit(el.nativeElement.value);
      if(ngModel) {
        ngModel.control.setValue(el.nativeElement.value)
      }
    }});

  }
}
