import {BrowserModule} from "@angular/platform-browser";
import {NgModule} from "@angular/core";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {HttpModule} from "@angular/http";
import {RouterModule} from "@angular/router";
import {AppComponent} from "./app.component";
import {UserEditComponent} from "./components/user/edit/user-edit.component";
import {UserViewComponent} from "./components/user/view/user-view.component";
import {UserService} from "./components/user/user.service";
import {UserListComponent} from "./components/user/list/user-list.component";
import {EventListComponent} from "./components/event/list/event-list.component";
import {EventService} from "./components/event/event.service";
import {EventConfigurationComponent} from "./components/event/configuration/event-configuration.component";
import {PrinterListComponent} from "./components/printer/list/printer-list.component";
import {PrinterService} from "./components/printer/printer.service";
import {CloseDetailComponent} from "./components/close-detail/close-detail.component";
import {KeysPipe} from "./keys.pipe";
import {WindowRef} from "./window.service";
import {DragulaModule} from "ng2-dragula";
import {UserNotifierService} from "./components/user/user-notifier.service";

@NgModule({
  declarations: [
    AppComponent,
    UserEditComponent,
    UserViewComponent,
    UserListComponent,
    EventListComponent,
    EventConfigurationComponent,
    PrinterListComponent,
    CloseDetailComponent,
    KeysPipe
  ],
  imports: [
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    HttpModule,
    DragulaModule,
    RouterModule.forRoot([
      { path: 'user/new', component: UserEditComponent },
      { path: 'user/edit/:userId', component: UserEditComponent },
      { path: 'user/:userId', component: UserViewComponent },
      { path: 'event/config/:eventId', component: EventConfigurationComponent}
    ])
  ],
  providers: [UserService, EventService, PrinterService, WindowRef, UserNotifierService],
  bootstrap: [AppComponent]
})
export class AppModule { }
