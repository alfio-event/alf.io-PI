import { AlfioPIPage } from './app.po';

describe('alfio-pi App', function() {
  let page: AlfioPIPage;

  beforeEach(() => {
    page = new AlfioPIPage();
  });

  it('should display message saying app works', () => {
    page.navigateTo();
    expect(page.getParagraphText()).toEqual('app works!');
  });
});
