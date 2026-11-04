import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApiError } from '../../api/api-error';
import { UsageToday } from '../../api/api.models';
import { ApiService } from '../../api/api.service';
import { expectNoA11yViolations } from '../../../testing/a11y';
import { USAGE_TODAY, apiError } from '../../../testing/fakes';
import { Usage } from './usage';

describe('Usage screen', () => {
  let usageToday: jasmine.Spy;

  async function setup(result: UsageToday | ApiError = USAGE_TODAY): Promise<{ fixture: ComponentFixture<Usage>; el: HTMLElement }> {
    usageToday = jasmine.createSpy('usageToday').and.callFake(() =>
      result instanceof ApiError ? Promise.reject(result) : Promise.resolve(result));
    await TestBed.configureTestingModule({
      imports: [Usage],
      providers: [{ provide: ApiService, useValue: { usageToday } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(Usage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows tokens used, the limit and what remains', async () => {
    const { el } = await setup();

    expect(el.querySelector('.numbers')?.textContent).toContain('600');
    expect(el.querySelector('.numbers')?.textContent).toContain('1000');
    expect(el.querySelector('.numbers')?.textContent).toContain('400 remaining');
    expect(el.textContent).toContain('estimates');
  });

  it('exposes the share used as a progress bar for assistive technology', async () => {
    const { el } = await setup();

    const bar = el.querySelector('[role="progressbar"]') as HTMLElement;
    expect(bar.getAttribute('aria-valuenow')).toBe('60');
    expect(bar.getAttribute('aria-valuemin')).toBe('0');
    expect(bar.getAttribute('aria-valuemax')).toBe('100');
    expect(bar.getAttribute('aria-label')).toBeTruthy();
    expect((el.querySelector('.fill') as HTMLElement).style.width).toBe('60%');
  });

  it('switches the bar to a warning colour near the limit, and caps it at 100 percent', async () => {
    const near = await setup({ ...USAGE_TODAY, tokensUsed: 900, remainingTokens: 100 });
    expect(near.el.querySelector('.fill')?.classList.contains('near')).toBeTrue();
    TestBed.resetTestingModule();

    const over = await setup({ ...USAGE_TODAY, tokensUsed: 1200, remainingTokens: 0 });
    expect(over.el.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('100');
  });

  it('does not divide by zero when no limit is set', async () => {
    const { el } = await setup({ ...USAGE_TODAY, dailyLimit: 0, tokensUsed: 0, remainingTokens: 0 });

    expect(el.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('0');
  });

  it('shows an error instead of numbers when the call fails', async () => {
    const { el } = await setup(apiError(401, 'UNAUTHENTICATED'));

    expect(el.querySelector('.alert-danger')?.textContent).toContain('session has ended');
    expect(el.querySelector('.numbers')).toBeNull();
  });

  it('reloads when Refresh is pressed', async () => {
    const { fixture, el } = await setup();

    (el.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(usageToday).toHaveBeenCalledTimes(2);
  });

  it('has no accessibility violations (automated axe check, WCAG 2 A and AA)', async () => {
    const { el } = await setup();
    await expectNoA11yViolations(el);
  });
});
