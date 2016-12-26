import {PipeTransform, Pipe} from "@angular/core";
@Pipe({name: 'keys', pure: false})
export class KeysPipe implements PipeTransform {
  transform(value: Object): Array<string> {
    return value ? Object.keys(value) : [];
  }
}
