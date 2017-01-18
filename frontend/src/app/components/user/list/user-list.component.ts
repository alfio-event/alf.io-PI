import { Component, OnInit } from '@angular/core';
import {UserService, User} from "../user.service";
import {DragulaService} from "ng2-dragula";
import {UserNotifierService} from "../user-notifier.service";
import {ProgressManager} from "../../../ProgressManager";

@Component({
  selector: 'user-list',
  templateUrl: './user-list.component.html',
})
export class UserListComponent implements OnInit {

  users: Array<User>;
  progressManager: ProgressManager;

  constructor(private userService: UserService,
              private dragulaService: DragulaService,
              private userNotifierService: UserNotifierService) {
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
    userNotifierService.userCreated$.subscribe(username => this.loadUsers());
    userNotifierService.passwordReset$.subscribe(username => this.loadUsers());
    this.progressManager = new ProgressManager();
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  private loadUsers(): void {
    this.progressManager.monitorCall(() => this.userService.getUsers())
      .subscribe(res => this.users = res);
  }

}
