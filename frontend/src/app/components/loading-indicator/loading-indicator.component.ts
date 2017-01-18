import {Component, OnInit, Input} from "@angular/core";
import {Observable} from "rxjs";

@Component({
  selector: 'loading-indicator',
  template: `<span class="text-muted" *ngIf="workInProgress"><i class="fa fa-spinner fa-spin"></i></span>`
})
export class LoadingIndicatorComponent implements OnInit {

  @Input()
  observable: Observable<boolean>;
  workInProgress: boolean = false;

  ngOnInit(): void {
    this.observable.subscribe(progress => this.workInProgress = progress);
  }

}
