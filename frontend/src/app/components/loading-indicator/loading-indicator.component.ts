import {Component, OnInit, Input} from "@angular/core";
import {Observable} from "rxjs";

@Component({
  selector: 'loading-indicator',
  template: `<span class="text-muted" *ngIf="workInProgress"><i class="fa fa-spinner fa-spin" [class.fa-5x]="big"></i></span>`
})
export class LoadingIndicatorComponent implements OnInit {

  @Input()
  observable: Observable<boolean>;
  @Input()
  big: boolean;
  workInProgress: boolean = false;

  ngOnInit(): void {
    this.observable.subscribe(progress => this.workInProgress = progress);
  }

}
