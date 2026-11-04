import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApiError } from '../../api/api-error';
import { DocumentInfo, IngestionResult } from '../../api/api.models';
import { ApiService } from '../../api/api.service';
import { expectNoA11yViolations } from '../../../testing/a11y';
import { apiError } from '../../../testing/fakes';
import { Documents, MAX_FILE_BYTES } from './documents';

const DOCS: DocumentInfo[] = [
  { id: 1, title: 'Returns policy', version: 2, visibility: 'PUBLIC', chunks: 4, embeddedForProvider: 4 },
  { id: 2, title: 'Refund approvals', version: 1, visibility: 'AGENT_ONLY', chunks: 3, embeddedForProvider: 0 },
];

describe('Documents screen', () => {
  let upload: jasmine.Spy;

  async function setup(options: { docs?: DocumentInfo[] | ApiError; result?: IngestionResult | ApiError } = {}):
    Promise<{ fixture: ComponentFixture<Documents>; el: HTMLElement }> {
    const docs = options.docs ?? DOCS;
    const result = options.result ?? { documentId: 3, title: 'New', version: 1, chunks: 2, status: 'CREATED' as const };
    upload = jasmine.createSpy('upload').and.callFake(() =>
      result instanceof ApiError ? Promise.reject(result) : Promise.resolve(result));
    await TestBed.configureTestingModule({
      imports: [Documents],
      providers: [{
        provide: ApiService,
        useValue: { documents: () => (docs instanceof ApiError ? Promise.reject(docs) : Promise.resolve(docs)), upload },
      }],
    }).compileComponents();
    const fixture = TestBed.createComponent(Documents);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function choose(fixture: ComponentFixture<Documents>, el: HTMLElement, file: File): void {
    const input = el.querySelector('#docFile') as HTMLInputElement;
    const transfer = new DataTransfer();
    transfer.items.add(file);
    input.files = transfer.files;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  async function submit(fixture: ComponentFixture<Documents>, el: HTMLElement): Promise<void> {
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const text = (el: HTMLElement) => el.textContent ?? '';
  const uploadButton = (el: HTMLElement) => el.querySelector('button[type=submit]') as HTMLButtonElement;

  it('lists documents and marks one that needs re-embedding', async () => {
    const { el } = await setup();
    const rows = el.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[1].textContent).toContain('Agents only');
    expect(rows[1].textContent).toContain('Needs re-embedding');
    expect(rows[0].textContent).not.toContain('Needs re-embedding');
  });

  it('keeps Upload disabled until a file is chosen', async () => {
    const { fixture, el } = await setup();
    expect(uploadButton(el).disabled).toBeTrue();
    choose(fixture, el, new File(['# Hi'], 'a.md'));
    expect(uploadButton(el).disabled).toBeFalse();
  });

  it('rejects a wrong file type without calling the server', async () => {
    const { fixture, el } = await setup();
    choose(fixture, el, new File(['x'], 'a.pdf'));
    expect(text(el)).toContain('Choose a plain-text .md or .txt file');
    expect(uploadButton(el).disabled).toBeTrue();
    expect(upload).not.toHaveBeenCalled();
  });

  it('rejects an empty file and an oversize file', async () => {
    const { fixture, el } = await setup();
    choose(fixture, el, new File([], 'empty.md'));
    expect(text(el)).toContain('Choose a plain-text');
    expect(uploadButton(el).disabled).toBeTrue();
    choose(fixture, el, new File([new Uint8Array(MAX_FILE_BYTES + 1)], 'big.md'));
    expect(text(el)).toContain('Choose a plain-text');
    expect(uploadButton(el).disabled).toBeTrue();
    expect(upload).not.toHaveBeenCalled();
  });

  it('uploads with title and visibility, then reports the result', async () => {
    const { fixture, el } = await setup();
    choose(fixture, el, new File(['# Hi'], 'a.md'));
    const title = el.querySelector('#docTitle') as HTMLInputElement;
    title.value = 'My doc';
    title.dispatchEvent(new Event('input'));
    const select = el.querySelector('#docVisibility') as HTMLSelectElement;
    select.value = 'AGENT_ONLY';
    select.dispatchEvent(new Event('change'));
    await submit(fixture, el);

    expect(upload).toHaveBeenCalledWith(jasmine.any(File), 'My doc', 'AGENT_ONLY');
    expect(text(el)).toContain('Added "New" (version 1, 2 passages).');
  });

  it('explains a duplicate upload', async () => {
    const { fixture, el } = await setup({
      result: { documentId: 1, title: 'Returns policy', version: 2, chunks: 4, status: 'DUPLICATE' },
    });
    choose(fixture, el, new File(['x'], 'a.md'));
    await submit(fixture, el);
    expect(text(el)).toContain('nothing changed');
  });

  it('explains a re-embedded upload', async () => {
    const { fixture, el } = await setup({
      result: { documentId: 1, title: 'Returns policy', version: 2, chunks: 4, status: 'REINDEXED' },
    });
    choose(fixture, el, new File(['x'], 'a.md'));
    await submit(fixture, el);
    expect(text(el)).toContain('embedded again');
  });

  it('shows a refusal from the server and keeps the file for another try', async () => {
    const { fixture, el } = await setup({ result: apiError(400, 'INVALID_DOCUMENT') });
    choose(fixture, el, new File(['x'], 'a.md'));
    await submit(fixture, el);
    expect(el.querySelector('.alert-danger')?.getAttribute('role')).toBe('alert');
    expect(uploadButton(el).disabled).toBeFalse();
  });

  it('shows an error when the list cannot be loaded', async () => {
    const { el } = await setup({ docs: apiError(403, null) });
    expect(el.querySelector('.alert-danger')).not.toBeNull();
    expect(el.querySelector('table')).toBeNull();
  });

  it('shows an empty state', async () => {
    const { el } = await setup({ docs: [] });
    expect(text(el)).toContain('No documents are stored yet');
  });

  describe('accessibility (automated axe check, WCAG 2 A and AA)', () => {
    it('has no violations for the list and form', async () => {
      const { el } = await setup();
      await expectNoA11yViolations(el);
    });
  });
});
