import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApiError } from '../../api/api-error';
import { OrderDetail, OrderSummary } from '../../api/api.models';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../auth/auth.service';
import { expectNoA11yViolations } from '../../../testing/a11y';
import { ORDERS, ORDER_DETAIL, apiError } from '../../../testing/fakes';
import { Orders } from './orders';

describe('Orders screen', () => {
  let order: jasmine.Spy;

  async function setup(options: {
    agent?: boolean;
    orders?: OrderSummary[] | ApiError;
    detail?: OrderDetail | ApiError;
  } = {}): Promise<{ fixture: ComponentFixture<Orders>; el: HTMLElement }> {
    const orders = options.orders ?? ORDERS;
    const detail = options.detail ?? ORDER_DETAIL;
    order = jasmine.createSpy('order').and.callFake(() =>
      detail instanceof ApiError ? Promise.reject(detail) : Promise.resolve(detail));
    await TestBed.configureTestingModule({
      imports: [Orders],
      providers: [
        {
          provide: ApiService,
          useValue: {
            orders: () => (orders instanceof ApiError ? Promise.reject(orders) : Promise.resolve(orders)),
            order,
          },
        },
        { provide: AuthService, useValue: { isAgent: () => options.agent ?? false } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(Orders);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const text = (el: HTMLElement) => el.textContent ?? '';

  it('lists the orders with their status and promised date', async () => {
    const { el } = await setup();

    const rows = el.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('ORD-1002');
    expect(rows[1].textContent).toContain('DELAYED');
    expect(rows[1].textContent).toContain('Nov 10, 2026');
    expect(el.querySelector('h1')?.textContent).toBe('Your orders');
  });

  it('opens an order and shows its milestones in time order', async () => {
    const { fixture, el } = await setup();

    (el.querySelectorAll('tbody button')[1] as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(order).toHaveBeenCalledWith('ORD-1001');
    const milestones = Array.from(el.querySelectorAll('.timeline .what')).map((n) => n.textContent);
    expect(milestones).toEqual(['PLACED', 'DELAYED']);
  });

  it('tells the person the order was not found, whether it does not exist or is not theirs', async () => {
    const { fixture, el } = await setup({ detail: apiError(404, null) });

    (el.querySelector('tbody button') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(el)).toContain('That order was not found');
    expect(el.querySelector('.detail')).toBeNull();
  });

  it('shows an empty state when there are no orders', async () => {
    const { el } = await setup({ orders: [] });

    expect(text(el)).toContain('There are no orders to show');
    expect(el.querySelector('table')).toBeNull();
  });

  it('shows an error when the list cannot be loaded', async () => {
    const { el } = await setup({ orders: apiError(503, 'MODEL_UNAVAILABLE') });

    expect(el.querySelector('.alert-danger')?.getAttribute('role')).toBe('alert');
  });

  it('gives a customer no way to look up other orders', async () => {
    const { el } = await setup({ agent: false });

    expect(el.querySelector('form.lookup')).toBeNull();
  });

  it('lets a support agent look up any order by reference', async () => {
    const { fixture, el } = await setup({ agent: true });
    expect(el.querySelector('h1')?.textContent).toBe('Recent orders');

    const input = el.querySelector('#lookup') as HTMLInputElement;
    input.value = 'ORD-2001';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (el.querySelector('form.lookup') as HTMLFormElement).dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(order).toHaveBeenCalledWith('ORD-2001');
  });

  describe('accessibility (automated axe check, WCAG 2 A and AA)', () => {
    it('has no violations for the list', async () => {
      const { el } = await setup();
      await expectNoA11yViolations(el);
    });

    it('has no violations with an order open and for an agent', async () => {
      const { fixture, el } = await setup({ agent: true });
      (el.querySelector('tbody button') as HTMLButtonElement).click();
      await fixture.whenStable();
      fixture.detectChanges();
      await expectNoA11yViolations(el);
    });
  });
});
