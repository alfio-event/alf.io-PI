import {Component, isDevMode} from "@angular/core";

@Component({
  selector: 'appCheckScanner',
  templateUrl: './check-scanner.component.html',
  styleUrls: [ './check-scanner.component.css' ]
})
export class CheckScannerComponent {

  originalText = `/=\nLet me explain something to you.\nUm, I am not "Mr. Lebowski". You're Mr. Lebowski.\nI'm the Dude. So that's what you call me.\nYou know, that or, uh, His Dudeness, or uh, Duder, or El Duderino if you're not into the whole brevity thing.\nZ=/`;
  scannedText = '';
  charsRead = 0;
  enterScannedAtTheEnd = false;
  errors = 0;

  onScan(text: string): void {
    let translated = '';
    if (text === 'Dead') {
      translated = '<strong class="text-danger">■</strong>';
    } else if (text === 'ArrowDown') {
      translated = '\n';
    } else if (text === 'Enter') {
      this.enterScannedAtTheEnd = true;
      return;
    } else {
      translated = this.compareInput(text);
      this.enterScannedAtTheEnd = false;
    }
    this.charsRead++;
    this.scannedText += translated;
  }

  get readComplete(): boolean {
    return this.charsRead === this.originalText.length;
  }

  reset(): void {
    this.enterScannedAtTheEnd = false;
    this.scannedText = '';
    this.charsRead = 0;
    this.errors = 0;
  }

  private compareInput(read: string): string {
    const idx = Math.min(this.originalText.length, this.charsRead);
    if (this.originalText[idx] !== read) {
      if (isDevMode()) {
        console.log('error!', this.originalText[idx], read);
      }
      this.errors++;
      return `<strong class="text-danger">${read}</strong>`;
    }
    return read;
  }

}
