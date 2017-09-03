import {Directive, ElementRef, EventEmitter, Output} from '@angular/core';

@Directive({
  selector: '[virtualKeyboard]'
})
export class VirtualKeyboardDirective {

  @Output()
  acceptedKeyboard: EventEmitter<String> = new EventEmitter();

  constructor(el: ElementRef) {
    let _a = this.acceptedKeyboard;
    jQuery(el.nativeElement).keyboard({type:'', layout: 'international', accepted: (event, keyboard) => {
      _a.emit(el.nativeElement.value);
    }});
  }
}
