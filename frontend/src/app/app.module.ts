import {BrowserModule} from "@angular/platform-browser";
import {NgModule} from "@angular/core";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {HttpClientModule} from "@angular/common/http";
import {RouterModule} from "@angular/router";
import {AppComponent} from "./app.component";
import {EventListComponent} from "./components/event/list/event-list.component";
import {EventService} from "./shared/event/event.service";
import {PrinterService} from "./components/printer/printer.service";
import {CloseDetailComponent} from "./components/close-detail/close-detail.component";
import {KeysPipe} from "./keys.pipe";
import {WindowRef} from "./window.service";
import { LoadingIndicatorComponent } from './components/loading-indicator/loading-indicator.component';
import { ScanLogEntriesComponent } from './components/scan-log-entries/scan-log-entries.component';
import {ScanLogService} from "./components/scan-log-entries/scan-log.service";
import { HomeComponent } from './components/home/home.component';
import { ScanLogComponent } from './components/scan-log/scan-log.component';
import {FilterScanLogEntries} from "./filter.pipe";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {ServerEventsService} from "./server-events.service";
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { ResponsiveLayoutComponent } from './components/responsive-layout/responsive-layout.component';
import { EventListPageComponent } from './components/event/list-page/event-list-page.component';
import { ConfirmPowerOffComponent } from './components/confirm-power-off/confirm-power-off.component';
import { CheckInComponent } from './components/check-in/check-in.component';
import {ScanListenerDirective} from "./components/check-in/scan-listener.directive";
import {ScanService} from "./scan-module/scan/scan.service";
import {ScanLogEntryReprintComponent} from "app/components/scan-log-entries/reprint/scan-log-entry-reprint.component";
import {SettingsComponent} from "./components/settings/settings.component";
import { VirtualKeyboardDirective } from './virtual-keyboard.directive';
import {ConfigurationService} from "./shared/configuration/configuration.service";
import { SystemInfoComponent } from './components/system-info/system-info.component';
import {SidebarWatchComponent} from "./components/sidebar/sidebar.watch.component";
import {QRCodeModule} from "angular2-qrcode";

@NgModule({
  declarations: [
    AppComponent,
    EventListComponent,
    CloseDetailComponent,
    KeysPipe,
    FilterScanLogEntries,
    LoadingIndicatorComponent,
    ScanLogEntriesComponent,
    HomeComponent,
    ScanLogComponent,
    ScanLogEntryReprintComponent,
    SidebarComponent,
    ResponsiveLayoutComponent,
    EventListPageComponent,
    ConfirmPowerOffComponent,
    SettingsComponent,
    CheckInComponent,
    ScanListenerDirective,
    VirtualKeyboardDirective,
    SystemInfoComponent,
    SidebarWatchComponent
  ],
    imports: [
        NgbModule,
        BrowserModule,
        FormsModule,
        ReactiveFormsModule,
        HttpClientModule,
        RouterModule.forRoot([
            {path: '', component: HomeComponent},
            {
                path: 'scan-log', children: [
                    {path: '', redirectTo: 'view', pathMatch: 'full'},
                    {path: 'view', component: ScanLogComponent},
                    {path: 'event/:eventKey/entry/:entryId/reprint', component: ScanLogEntryReprintComponent}
                ]
            },
            {path: 'check-in', component: CheckInComponent},
            {path: 'events', component: EventListPageComponent},
            {path: 'settings', component: SettingsComponent},
            {path: 'power-off', component: ConfirmPowerOffComponent},
            {path: 'system-info', component: SystemInfoComponent}

        ]),
        QRCodeModule
    ],
  providers: [EventService, PrinterService, WindowRef, ScanLogService, ServerEventsService, ScanService, ConfigurationService],
  bootstrap: [AppComponent]
})
export class AppModule { }
