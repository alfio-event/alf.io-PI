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

import { Injectable } from '@angular/core';
import { Subject }    from 'rxjs';
@Injectable()
export class UserNotifierService {
  // Observable string sources
  private userCreatedSource = new Subject<string>();
  private passwordResetSource = new Subject<string>();
  // Observable string streams
  userCreated$ = this.userCreatedSource.asObservable();
  passwordReset$ = this.passwordResetSource.asObservable();
  // Service message commands
  userCreated(username: string) {
    this.userCreatedSource.next(username);
  }
  passwordReset(username: string) {
    this.passwordResetSource.next(username);
  }
}
