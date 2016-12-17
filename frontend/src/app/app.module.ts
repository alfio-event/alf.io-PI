import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {HttpModule, CookieXSRFStrategy, XSRFStrategy} from '@angular/http';
import { RouterModule }   from '@angular/router';
import { AppComponent } from './app.component';
import {UserEditComponent} from "./components/user/edit/user-edit.component";
import {UserViewComponent} from "./components/user/view/user-view.component";
import {UserService} from "./components/user/user.service";
import { UserListComponent } from './components/user/list/user-list/user-list.component';
import { EventListComponent } from './components/event/list/event-list/event-list.component';
import {EventService} from "./components/event/event.service";

@NgModule({
  declarations: [
    AppComponent,
    UserEditComponent,
    UserViewComponent,
    UserListComponent,
    EventListComponent
  ],
  imports: [
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    HttpModule,
    RouterModule.forRoot([
      {
        path: 'user/new',
        component: UserEditComponent
      }, {
        path: 'user/edit/:userId',
        component: UserEditComponent
      }, {
        path: 'user/:userId',
        component: UserViewComponent
      }
    ])
  ],
  providers: [UserService, { provide: XSRFStrategy, useValue: new CookieXSRFStrategy('XSRF-TOKEN', 'X-XSRF-TOKEN') }, EventService],
  bootstrap: [AppComponent]
})
export class AppModule { }
