import {Component, OnInit, Input, Output, EventEmitter} from "@angular/core";
import {UserService, User, NewUser, UserWithPassword} from "../user.service";
import "rxjs/add/operator/switchMap";
import {isNullOrUndefined} from "util";
import {FormBuilder, Validators, FormControl} from "@angular/forms";
import {WindowRef} from "../../../window.service";
import {ActivatedRoute, Params} from "@angular/router";
import {UserNotifierService} from "../user-notifier.service";

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
              private userService: UserService,
              private windowRef: WindowRef,
              private userNotifierService: UserNotifierService) {}

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
        this.userQRCodeUrl = this.getQRCodeURL(res);
        this.userNotifierService.userCreated(res.username);
      })
  }

  resetPassword(): void {
    this.userService.resetPassword(this.user.id)
      .subscribe(res => {
        this.user = res;
        this.displayQRCode = true;
        this.userQRCodeUrl = this.getQRCodeURL(res);
        this.userNotifierService.passwordReset(this.user.username);
      });
  }

  onSubmit(): void {
    this.userService.saveUser(this.username.value)
      .subscribe(res => {
        this.user = res;
        this.displayQRCode = true;
        this.userQRCodeUrl = this.getQRCodeURL(res);

      });
  }

  private getQRCodeURL(res: UserWithPassword): string {
    let $window = this.windowRef.nativeWindow;
    let password = $window.encodeURIComponent($window.btoa(res.password));
    return `/api/internal/users/${res.id}/qr-code?password=${password}`;
  }

  getUserWithPassword(): UserWithPassword {
    return <UserWithPassword>this.user;
  }



}
