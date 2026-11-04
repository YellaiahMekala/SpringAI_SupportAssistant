import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Me } from './api/api.models';
import { App } from './app';
import { AuthService } from './auth/auth.service';
import { expectNoA11yViolations } from '../testing/a11y';

describe('App shell', () => {
  let signOut: jasmine.Spy;

  async function setup(me: Me | null): Promise<{ fixture: ComponentFixture<App>; el: HTMLElement }> {
    signOut = jasmine.createSpy('signOut');
    const roles = me?.roles ?? [];
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            me: signal(me),
            isAgent: () => roles.includes('ROLE_SUPPORT_AGENT'),
            displayName: () => me?.displayName ?? me?.subject ?? '',
            signOut,
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const customer: Me = { subject: 's1', roles: ['ROLE_CUSTOMER'], displayName: 'Alice Demo' };
  const agent: Me = { subject: 's2', roles: ['ROLE_SUPPORT_AGENT'], displayName: 'Agent Demo' };

  it('shows the three screens in the navigation and who is signed in', async () => {
    const { el } = await setup(customer);

    const links = Array.from(el.querySelectorAll('nav a')).map((a) => a.textContent?.trim());
    expect(links).toEqual(['Ask support', 'Orders', 'Usage']);
    expect(el.querySelector('.who')?.textContent).toContain('Alice Demo');
    expect(el.querySelector('.who .badge')).toBeNull();
  });

  it('adds the Documents screen to the navigation for a support agent only', async () => {
    const { el } = await setup(agent);

    const links = Array.from(el.querySelectorAll('nav a')).map((a) => a.textContent?.trim());
    expect(links).toEqual(['Ask support', 'Orders', 'Usage', 'Documents']);
  });

  it('labels a support agent', async () => {
    const { el } = await setup(agent);

    expect(el.querySelector('.who .badge')?.textContent).toContain('Support agent');
  });

  it('signs out when the button is pressed', async () => {
    const { el } = await setup(customer);

    (el.querySelector('.user button') as HTMLButtonElement).click();

    expect(signOut).toHaveBeenCalledTimes(1);
  });

  it('explains instead of showing broken screens when the account has no role', async () => {
    const { el } = await setup(null);

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('No access');
    expect(el.querySelector('nav a')).not.toBeNull();
    expect(el.querySelector('router-outlet')).toBeNull();
  });

  it('has a skip link and a main landmark for keyboard and screen-reader users', async () => {
    const { el } = await setup(customer);

    expect(el.querySelector('a.skip-link')?.getAttribute('href')).toBe('#main');
    expect(el.querySelector('main#main')).not.toBeNull();
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe('Main');
  });

  it('has no accessibility violations (automated axe check, WCAG 2 A and AA)', async () => {
    const { el } = await setup(customer);
    await expectNoA11yViolations(el);
  });
});
