import axe from 'axe-core';

/** Fails the test on WCAG 2 A/AA axe violations in the rendered component. */
export async function expectNoA11yViolations(root: HTMLElement): Promise<void> {
  const results = await axe.run(root, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
  });
  const summary = results.violations.map(
    (violation) => `${violation.id}: ${violation.help} [${violation.nodes.map((n) => n.target.join(' ')).join(' | ')}]`,
  );
  expect(summary).toEqual([]);
}
