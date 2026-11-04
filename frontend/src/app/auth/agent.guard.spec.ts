import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { agentGuard } from './agent.guard';

describe('agentGuard', () => {
  function run(agent: boolean): boolean | UrlTree {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAgent: () => agent } },
        { provide: Router, useValue: { createUrlTree: (commands: string[]) => ({ commands }) as unknown as UrlTree } },
      ],
    });
    return TestBed.runInInjectionContext(() => agentGuard({} as never, [])) as boolean | UrlTree;
  }

  it('lets a support agent through', () => {
    expect(run(true)).toBeTrue();
  });

  it('sends anyone else to the chat', () => {
    expect((run(false) as unknown as { commands: string[] }).commands).toEqual(['/chat']);
  });
});
