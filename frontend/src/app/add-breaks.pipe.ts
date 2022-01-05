import {Pipe, PipeTransform} from "@angular/core";

@Pipe({name: 'breaks', pure: true})
export class AddBreaksPipe implements PipeTransform {
  transform(value: string): string {
    return value?.replace(/\n/g, '<br>');
  }
}
