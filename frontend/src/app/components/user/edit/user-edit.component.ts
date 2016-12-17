import {Component, OnInit} from "@angular/core";
import {UserService, User, NewUser, UserWithPassword} from "../user.service";
import {ActivatedRoute, Params} from "@angular/router";
import "rxjs/add/operator/switchMap";
import {isNullOrUndefined} from "util";
import {FormBuilder, Validators, FormControl} from "@angular/forms";

@Component({
  selector: 'user-edit',
  templateUrl: './user-edit.component.html'
})
export class UserEditComponent implements OnInit {

  private existing: boolean = false;
  private user: User;
  username : FormControl;
  displayReset: boolean = false;
  displayQRCode: boolean = false;
  userQRCodeUrl: string;

  constructor(private route: ActivatedRoute,
              private formBuilder: FormBuilder,
              private userService: UserService) {}

  ngOnInit(): void {
    this.route.params
      .switchMap((params: Params) => {
        let userId = params['userId'];
        this.existing = !isNullOrUndefined(userId);
        if(this.existing) {
          return this.userService.getUser(userId);
        } else {
          return Promise.resolve(new NewUser());
        }
      }).subscribe((user: User) => {
        this.user = user;
        if(!(user instanceof NewUser)) {
          this.username.setValue(user.username);
          this.displayReset = true;
        }
      });
      this.username = this.formBuilder.control('', Validators.required);
      this.username.registerOnChange(val => this.displayQRCode = false);
  }

  create(): void {
    this.userService.saveUser(this.username.value)
      .subscribe(res => {
        this.user = res;
        this.displayQRCode = true;
        this.userQRCodeUrl = `/api/users/${res.id}/qr-code?password=${res.password}`;
      })
  }

  resetPassword(): void {
    this.userService.resetPassword(this.user.id)
      .subscribe(res => {
        this.user = res;
        this.displayQRCode = true;
        this.userQRCodeUrl = `/api/users/${res.id}/qr-code?password=${res.password}`;
      });
  }

  onSubmit(): void {
    this.userService.saveUser(this.username.value)
      .subscribe(res => {
        this.user = res;
        this.displayQRCode = true;
        this.userQRCodeUrl = `/api/users/${res.id}/qr-code?password=${res.password}`;
      });
  }

  getUserWithPassword(): UserWithPassword {
    return <UserWithPassword>this.user;
  }



}
