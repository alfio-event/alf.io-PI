import {Directive, ElementRef, EventEmitter, Optional, Output} from '@angular/core';
import {NgModel} from "@angular/forms";

@Directive({
  selector: '[virtualKeyboard]'
})
export class VirtualKeyboardDirective {

  @Output()
  change: EventEmitter<String> = new EventEmitter();

  constructor(el: ElementRef, @Optional() ngModel: NgModel) {
    let _a = this.change;
    jQuery(el.nativeElement).keyboard({type:'', layout: 'international', accepted: (event, keyboard) => {
      if(ngModel) {
        ngModel.control.setValue(el.nativeElement.value)
      }
      _a.emit(el.nativeElement.value);
    }});

  }
}
