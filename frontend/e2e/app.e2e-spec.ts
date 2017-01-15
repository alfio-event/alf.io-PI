import { AlfioPiPage } from './app.po';

describe('alfio-pi App', function() {
  let page: AlfioPiPage;

  beforeEach(() => {
    page = new AlfioPiPage();
  });

  it('should display message saying app works', () => {
    page.navigateTo();
    expect(page.getParagraphText()).toEqual('app works!');
  });
});
