import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let token: string | null;

  beforeEach(() => {
    token = 'access-token-1';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { accessToken: () => token } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('adds the bearer token to calls to the application API', () => {
    http.get('/api/v1/me').subscribe();

    const request = controller.expectOne('/api/v1/me');
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token-1');
    request.flush({});
  });

  it('never sends the token to any other address', () => {
    http.get('https://other.example.com/data').subscribe();
    http.get('/assets/config.json').subscribe();

    const first = controller.expectOne('https://other.example.com/data');
    const second = controller.expectOne('/assets/config.json');
    expect(first.request.headers.has('Authorization')).toBeFalse();
    expect(second.request.headers.has('Authorization')).toBeFalse();
    first.flush({});
    second.flush({});
  });

  it('sends the call without a header when there is no valid token', () => {
    token = null;
    http.get('/api/v1/me').subscribe();

    const request = controller.expectOne('/api/v1/me');
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
  });
});
