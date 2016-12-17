import { Component, OnInit } from '@angular/core';
import {UserService, User} from "../../user.service";

@Component({
  selector: 'user-list',
  templateUrl: './user-list.component.html',
})
export class UserListComponent implements OnInit {

  users: Array<User>;

  constructor(private userService: UserService) { }

  ngOnInit(): void {
    this.userService.getUsers()
      .subscribe(res => this.users = res)
  }

}
