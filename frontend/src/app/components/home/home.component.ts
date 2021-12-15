import {Component, OnInit, OnDestroy} from '@angular/core';
import {DragulaService} from "ng2-dragula";

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit, OnDestroy {

  constructor(private dragulaService: DragulaService) { }

  ngOnInit() {
    this.dragulaService.createGroup('users-bag', {
      copy: true,
      moves: function (el, container, handle) {
        return handle.className.includes('handle');
      },
      accepts: function (el: any, target: Element, source: any, sibling: any) {
        let accept = false;
        if(target != null && target.attributes.getNamedItem('drop-allowed') != null) {
          accept = target.attributes.getNamedItem('drop-allowed').value == "true";
        }
        return accept;
      }
    });
  }

  ngOnDestroy(): void {
    this.dragulaService.destroy('users-bag');
  }

}
