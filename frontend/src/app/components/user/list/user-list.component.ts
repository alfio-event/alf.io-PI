import { Component, OnInit } from '@angular/core';
import {UserService, User} from "../user.service";
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
              private userNotifierService: UserNotifierService) {
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
