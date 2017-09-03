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
import {EventService} from "./shared/event/event.service";
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
import {ServerEventsService} from "./server-events.service";
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { UserPrinterComponent } from './components/user-printer/user-printer.component';
import { ResponsiveLayoutComponent } from './components/responsive-layout/responsive-layout.component';
import { EventListPageComponent } from './components/event/list-page/event-list-page.component';
import { ConfirmPowerOffComponent } from './components/confirm-power-off/confirm-power-off.component';
import { CheckInComponent } from './components/check-in/check-in.component';
import {ScanListenerDirective} from "./components/check-in/scan-listener.directive";
import {ScanService} from "./scan-module/scan/scan.service";
import {ScanLogEntryReprintComponent} from "app/components/scan-log-entries/reprint/scan-log-entry-reprint.component";
import { QRCodeModule } from 'angular2-qrcode';
import {SettingsComponent} from "./components/settings/settings.component";
import { VirtualKeyboardDirective } from './virtual-keyboard.directive';
import {ConfigurationService} from "./shared/configuration/configuration.service";

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
    ScanLogComponent,
    ScanLogEntryReprintComponent,
    SidebarComponent,
    UserPrinterComponent,
    ResponsiveLayoutComponent,
    EventListPageComponent,
    ConfirmPowerOffComponent,
    SettingsComponent,
    CheckInComponent,
    ScanListenerDirective,
    VirtualKeyboardDirective
  ],
  imports: [
    NgbModule.forRoot(),
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    HttpModule,
    DragulaModule,
    QRCodeModule,
    RouterModule.forRoot([
      { path: '', component: HomeComponent, children: [
        { path: 'user/new', component: UserEditComponent },
        { path: 'user/edit/:userId', component: UserEditComponent }
      ] },
      { path: 'scan-log', children: [
        { path: '', redirectTo: 'view', pathMatch: 'full' },
        { path: 'view', component: ScanLogComponent },
        { path: 'event/:eventId/entry/:entryId/reprint', component: ScanLogEntryReprintComponent }
      ]},
      { path: 'users-printers', component: UserPrinterComponent, children: [
        { path: 'user/new', component: UserEditComponent },
        { path: 'user/edit/:userId', component: UserEditComponent }
      ]},
      { path: 'check-in', component: CheckInComponent},
      { path: 'events', component: EventListPageComponent},
      { path: 'settings', component: SettingsComponent},
      { path: 'power-off', component: ConfirmPowerOffComponent}

    ])
  ],
  providers: [UserService, EventService, PrinterService, WindowRef, UserNotifierService, ScanLogService, ServerEventsService, ScanService, ConfigurationService],
  bootstrap: [AppComponent]
})
export class AppModule { }
