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
import {Observable, Subject} from "rxjs";
import { tap } from "rxjs/operators"

export class ProgressManager {

  private source: Subject<boolean>;
  observable: Observable<boolean>;

  constructor() {
    this.source = new Subject<boolean>();
    this.observable = this.source.asObservable();
  }

  monitorCall<T>(call: () => Observable<T>): Observable<T> {
    this.source.next(true);
    let observable: Observable<T> = call();
    return observable.pipe(tap(v => this.source.next(false), null, () => this.source.next(false)));
  }

}
