/** One dispatched server-sent event. */
export interface SseMessage {
  event: string;
  data: string;
}

/** Incremental text/event-stream parser (handles chunk boundaries). */
export class SseParser {
  private buffer = '';
  private event = '';
  private data: string[] = [];
  private hasData = false;
  /** True right after a CR that ended a chunk: a LF at the start of the next chunk belongs to that CRLF. */
  private skipNextLf = false;

  /** Feeds more text and returns the events completed by it. */
  push(chunk: string): SseMessage[] {
    if (this.skipNextLf && chunk.startsWith('\n')) {
      chunk = chunk.slice(1);
    }
    if (chunk.length > 0) {
      this.skipNextLf = false;
    }
    this.buffer += chunk;

    const messages: SseMessage[] = [];
    let lineEnd: number;
    while ((lineEnd = this.findLineEnd()) >= 0) {
      const line = this.buffer.slice(0, lineEnd);
      let consumed = lineEnd + 1;
      if (this.buffer[lineEnd] === '\r') {
        if (this.buffer[lineEnd + 1] === '\n') {
          consumed++;
        } else if (lineEnd + 1 === this.buffer.length) {
          // The CR is the last character received; a LF may still arrive as the next chunk's first character.
          this.skipNextLf = true;
        }
      }
      this.buffer = this.buffer.slice(consumed);
      const message = this.processLine(line);
      if (message) {
        messages.push(message);
      }
    }
    return messages;
  }

  /** Index of the next line ending (LF or CR), or -1 when the buffer holds only a partial line. */
  private findLineEnd(): number {
    for (let i = 0; i < this.buffer.length; i++) {
      const c = this.buffer[i];
      if (c === '\n' || c === '\r') {
        return i;
      }
    }
    return -1;
  }

  private processLine(line: string): SseMessage | null {
    if (line === '') {
      return this.dispatch();
    }
    if (line.startsWith(':')) {
      return null;
    }
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }
    if (field === 'event') {
      this.event = value;
    } else if (field === 'data') {
      this.data.push(value);
      this.hasData = true;
    }
    return null;
  }

  private dispatch(): SseMessage | null {
    const message = this.hasData ? { event: this.event || 'message', data: this.data.join('\n') } : null;
    this.event = '';
    this.data = [];
    this.hasData = false;
    return message;
  }
}
