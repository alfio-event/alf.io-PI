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

import {PipeTransform, Pipe} from "@angular/core";
import {ScanLogEntryWithEvent} from "./components/scan-log-entries/scan-log-entries.component";
import {ScanLogEntry} from "./components/scan-log-entries/scan-log.service";
import {isNullOrUndefined} from "util";
@Pipe({name: 'filter'})
export class FilterScanLogEntries implements PipeTransform {
  transform(value: Array<ScanLogEntryWithEvent>, term): Array<ScanLogEntryWithEvent> {
    if(isNullOrUndefined(term)) {
      return value;
    }
    return value.filter(ee => FilterScanLogEntries.flattenEntry(ee.entry).includes(term.toLowerCase()));
  }

  private static flattenEntry(entry: ScanLogEntry): string {
    return `${entry.ticket.firstName}###${entry.ticket.lastName}###${entry.ticket.email}`.toLowerCase()
  }
}
