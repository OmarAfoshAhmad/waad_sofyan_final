import { describe, it, expect, beforeEach, vi } from 'vitest';

/**
 * Imported fresh per test on purpose. canEncodeWebp caches its answer for
 * the lifetime of the module, which is right in a browser -- one page load,
 * one set of capabilities -- and wrong in a file that deliberately pretends
 * to be several different browsers in a row.
 */
async function loadModule() {
  vi.resetModules();
  return import('../profile-photo');
}

/**
 * The upload path re-encodes a chosen photo before sending it. Two things
 * about that are easy to get wrong in ways nothing else would notice:
 *
 * 1. A browser that cannot encode WebP returns a PNG blob from the same call
 *    that asked for WebP -- silently, with no error. Naming that file .webp
 *    hands the server a name its bytes contradict, and the server's own
 *    container check then rejects a photo the user picked correctly.
 * 2. Resizing is the reason this exists. If the scale is ever computed the
 *    wrong way round, the code still "works": it uploads, it displays, and
 *    every avatar in the system is served from a full-resolution photo.
 */

class FakeImage {
  constructor() {
    setTimeout(() => {
      this.width = FakeImage.width;
      this.height = FakeImage.height;
      this.onload?.();
    }, 0);
  }
}
FakeImage.width = 3000;
FakeImage.height = 4000;

let encodedType;
let lastCanvas;

function installCanvas(supportedType) {
  encodedType = supportedType;
  vi.stubGlobal('Image', FakeImage);
  // Only the two object-URL helpers: replacing the whole URL global breaks
  // the dynamic import above, which needs the constructor.
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  vi.spyOn(document, 'createElement').mockImplementation((tag) => {
    if (tag !== 'canvas') return {};
    const canvas = {
      width: 0,
      height: 0,
      getContext: () => ({ drawImage: () => {}, imageSmoothingQuality: '' }),
      toBlob: (cb, type) => {
        // A browser that cannot encode the requested type does not fail --
        // it quietly returns something else.
        const produced = type === encodedType ? type : 'image/png';
        cb({ type: produced, size: 1234 });
      }
    };
    lastCanvas = canvas;
    return canvas;
  });
}

describe('preparing a photo for upload', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    lastCanvas = undefined;
  });

  it('re-encodes as WebP when the browser can', async () => {
    installCanvas('image/webp');

    const { prepareProfilePhoto } = await loadModule();
    const result = await prepareProfilePhoto(new File([], 'holiday.JPG', { type: 'image/jpeg' }));

    expect(result.format).toBe('webp');
    expect(result.file.name).toBe('holiday.webp');
    expect(result.file.type).toBe('image/webp');
  });

  it('falls back to JPEG, and names the file what it actually is', async () => {
    // Safari accepted 'image/webp' and returned PNG for years. The probe
    // catches that, so the fallback is chosen up front rather than shipping
    // a PNG under a .webp name.
    installCanvas('image/jpeg');

    const { prepareProfilePhoto } = await loadModule();
    const result = await prepareProfilePhoto(new File([], 'holiday.png', { type: 'image/png' }));

    expect(result.format).toBe('jpeg');
    expect(result.file.name).toBe('holiday.jpeg');
    expect(result.file.type).toBe('image/jpeg');
  });

  it('shrinks the long edge and keeps the proportions', async () => {
    FakeImage.width = 3000;
    FakeImage.height = 4000;
    installCanvas('image/webp');

    const { prepareProfilePhoto, MAX_PHOTO_EDGE } = await loadModule();
    await prepareProfilePhoto(new File([], 'tall.jpg', { type: 'image/jpeg' }));

    expect(lastCanvas.height).toBe(MAX_PHOTO_EDGE);
    expect(lastCanvas.width).toBe(Math.round((3000 / 4000) * MAX_PHOTO_EDGE));
  });

  it('never enlarges a photo that is already small', async () => {
    FakeImage.width = 120;
    FakeImage.height = 160;
    installCanvas('image/webp');

    const { prepareProfilePhoto } = await loadModule();
    await prepareProfilePhoto(new File([], 'small.jpg', { type: 'image/jpeg' }));

    expect(lastCanvas.width).toBe(120);
    expect(lastCanvas.height).toBe(160);
  });

  it('reports a file that cannot be read as an image instead of sending garbage', async () => {
    // jsdom exposes toBlob but never calls back, so the capability probe needs
    // a canvas here too even though this case never reaches the encoder.
    installCanvas('image/webp');
    vi.stubGlobal(
      'Image',
      class {
        constructor() {
          setTimeout(() => this.onerror?.(), 0);
        }
      }
    );

    const { prepareProfilePhoto } = await loadModule();
    await expect(prepareProfilePhoto(new File([], 'notes.txt', { type: 'text/plain' }))).rejects.toThrow(
      'IMAGE_UNREADABLE'
    );
  });
});
