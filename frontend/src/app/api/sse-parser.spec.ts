import { SseParser } from './sse-parser';

describe('SseParser', () => {
  it('parses one complete event', () => {
    const parser = new SseParser();

    expect(parser.push('event:token\ndata:"Hi"\n\n')).toEqual([{ event: 'token', data: '"Hi"' }]);
  });

  it('parses several events delivered in one chunk', () => {
    const parser = new SseParser();

    const messages = parser.push('event:token\ndata:"a"\n\nevent:token\ndata:"b"\n\nevent:done\ndata:{}\n\n');

    expect(messages.map((m) => m.event)).toEqual(['token', 'token', 'done']);
  });

  it('waits for the blank line before dispatching', () => {
    const parser = new SseParser();

    expect(parser.push('event:token\ndata:"Hi"\n')).toEqual([]);
    expect(parser.push('\n')).toEqual([{ event: 'token', data: '"Hi"' }]);
  });

  it('copes with an event split at any position, even inside a line', () => {
    const raw = 'event:token\ndata:"Hello"\n\nevent:done\ndata:{"a":1}\n\n';

    // Feed the same text one character at a time: the result must equal feeding it whole.
    const parser = new SseParser();
    const messages = [...raw].flatMap((char) => parser.push(char));

    expect(messages).toEqual([
      { event: 'token', data: '"Hello"' },
      { event: 'done', data: '{"a":1}' },
    ]);
  });

  it('accepts CRLF and lone CR line endings, including a CRLF split across chunks', () => {
    const parser = new SseParser();

    const first = parser.push('event:token\r\ndata:"a"\r\n\r\n');
    const second = [...parser.push('event:token\rdata:"b"\r'), ...parser.push('\r')];

    expect(first).toEqual([{ event: 'token', data: '"a"' }]);
    expect(second).toEqual([{ event: 'token', data: '"b"' }]);
  });

  it('joins several data lines with a line feed', () => {
    const parser = new SseParser();

    expect(parser.push('data:one\ndata:two\n\n')).toEqual([{ event: 'message', data: 'one\ntwo' }]);
  });

  it('drops exactly one space after the colon, as the specification says', () => {
    const parser = new SseParser();

    expect(parser.push('data: spaced\n\n')[0].data).toBe('spaced');
    expect(parser.push('data:  two\n\n')[0].data).toBe(' two');
  });

  it('ignores comments, unknown fields and events without data', () => {
    const parser = new SseParser();

    expect(parser.push(': keep-alive\nid:7\nretry:1000\nevent:token\n\n')).toEqual([]);
    expect(parser.push('event:token\ndata:"ok"\n\n')).toEqual([{ event: 'token', data: '"ok"' }]);
  });

  it('does not let one event leak its fields into the next', () => {
    const parser = new SseParser();

    parser.push('event:done\ndata:{}\n\n');
    expect(parser.push('data:plain\n\n')).toEqual([{ event: 'message', data: 'plain' }]);
  });
});
