/*
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
import {Directive, Output, HostListener, OnInit} from "@angular/core";
import {Observable} from "rxjs/Observable";
import {Subject} from "rxjs/Subject";
@Directive({
  selector: 'alfio-key-listener'
})
export class ScanListenerDirective {

  @Output()
  scanStream: Observable<string>;
  private scanSubject: Subject<string>;

  private buffer: Array<string> = [];

  constructor() {
    this.scanSubject = new Subject();
    this.scanStream = this.scanSubject.asObservable();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyboardInput(event: KeyboardEvent) {
    if(event.key === "Enter") {
      let read = this.buffer.splice(0, this.buffer.length).join('');
      console.log(`read ${read}`);
      this.scanSubject.next(read);
    } else if(event.key.length === 1) { // we're only interested in ASCII characters
      this.buffer.push(event.key);
    }
    event.stopImmediatePropagation();
    event.preventDefault();
  }

}
