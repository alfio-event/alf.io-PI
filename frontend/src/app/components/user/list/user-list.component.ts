import { Component, OnInit } from '@angular/core';
import {UserService, User} from "../user.service";
import {DragulaService} from "ng2-dragula";

@Component({
  selector: 'user-list',
  templateUrl: './user-list.component.html',
})
export class UserListComponent implements OnInit {

  users: Array<User>;

  constructor(private userService: UserService, private dragulaService: DragulaService) {
    dragulaService.setOptions('users-bag', {
      copy: true,
      moves: function (el, container, handle) {
        return handle.className.includes('handle');
      },
      accepts: function (el: any, target: Node, source: any, sibling: any) {
        let accept = false;
        if(target != null && target.attributes.getNamedItem('drop-allowed') != null) {
          accept = target.attributes.getNamedItem('drop-allowed').value == "true";
        }
        return accept;
      }
    });
  }

  ngOnInit(): void {
    this.userService.getUsers()
      .subscribe(res => this.users = res)
  }

}
