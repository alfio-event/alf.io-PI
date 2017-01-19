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
import { LoadingIndicatorComponent } from './components/loading-indicator/loading-indicator.component';
import { ScanLogEntriesComponent } from './components/scan-log-entries/scan-log-entries.component';
import {ScanLogService} from "./components/scan-log-entries/scan-log.service";
import { HomeComponent } from './components/home/home.component';
import { ScanLogComponent } from './components/scan-log/scan-log.component';
import {FilterScanLogEntries} from "./filter.pipe";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";

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
    KeysPipe,
    FilterScanLogEntries,
    LoadingIndicatorComponent,
    ScanLogEntriesComponent,
    HomeComponent,
    ScanLogComponent
  ],
  imports: [
    NgbModule.forRoot(),
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    HttpModule,
    DragulaModule,
    RouterModule.forRoot([
      { path: '', component: HomeComponent },
      { path: 'user/new', component: UserEditComponent },
      { path: 'user/edit/:userId', component: UserEditComponent },
      { path: 'scan-log/view', component: ScanLogComponent },
    ])
  ],
  providers: [UserService, EventService, PrinterService, WindowRef, UserNotifierService, ScanLogService],
  bootstrap: [AppComponent]
})
export class AppModule { }
