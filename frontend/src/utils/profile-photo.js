/**
 * Turns whatever the user picked into something a profile photo should be:
 * small enough to serve on every list and card, and in a format that stays
 * small.
 *
 * This runs in the browser rather than on the server on purpose. Encoding
 * WebP in Java needs a native image library the project does not have, and
 * adding one to resize avatars would be a dependency decision paying for
 * itself in nothing. The browser already has an encoder.
 *
 * It is an optimisation, not a control. The server still checks the type,
 * the bytes and the size, because this code does not run for anyone who
 * calls the endpoint directly.
 */

/** Long edge of the stored photo. An avatar is never rendered above this. */
export const MAX_PHOTO_EDGE = 512;

/** Below this, WebP stops being visibly better and starts being visibly worse. */
const WEBP_QUALITY = 0.82;

const JPEG_QUALITY = 0.85;

/**
 * Whether this browser can actually encode WebP, rather than whether it says
 * it can. Safari accepted the argument and silently produced PNG for years,
 * so the only honest test is to encode one pixel and read the type back.
 */
let webpSupport = null;

export async function canEncodeWebp() {
  if (webpSupport !== null) return webpSupport;
  try {
    const probe = document.createElement('canvas');
    // No toBlob at all: awaiting a callback that can never fire would hang
    // the upload with no error and no timeout.
    if (typeof probe.toBlob !== 'function') {
      webpSupport = false;
      return webpSupport;
    }
    probe.width = 1;
    probe.height = 1;
    const blob = await new Promise((resolve) => probe.toBlob(resolve, 'image/webp', 0.5));
    webpSupport = blob?.type === 'image/webp';
  } catch {
    webpSupport = false;
  }
  return webpSupport;
}

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('IMAGE_UNREADABLE'));
    };
    img.src = url;
  });
}

/**
 * @param {File} file the file the user chose
 * @returns {Promise<{file: File, format: 'webp'|'jpeg', originalBytes: number}>}
 *   the file to upload. `format` is what actually came out, not what was
 *   asked for -- a caller that wants to tell the user which one it got has
 *   to be told the truth.
 */
/** Already the target format and already small: re-encoding it only loses quality. */
const ALREADY_GOOD_ENOUGH_BYTES = 300 * 1024;

export async function prepareProfilePhoto(file) {
  // A file that is already WebP and already small is passed through
  // untouched. Decoding and re-encoding it would cost a second generation of
  // lossy compression to arrive at the same format it started in.
  if (file.type === 'image/webp' && file.size <= ALREADY_GOOD_ENOUGH_BYTES) {
    return { file, format: 'webp', originalBytes: file.size, reencoded: false };
  }

  // Settle the output format before doing the work, not after: the probe
  // creates a canvas of its own, and deciding first keeps the two apart.
  const wantsWebp = await canEncodeWebp();
  const type = wantsWebp ? 'image/webp' : 'image/jpeg';
  const quality = wantsWebp ? WEBP_QUALITY : JPEG_QUALITY;

  const image = await loadImage(file);

  const scale = Math.min(1, MAX_PHOTO_EDGE / Math.max(image.width, image.height));
  const width = Math.max(1, Math.round(image.width * scale));
  const height = Math.max(1, Math.round(image.height * scale));

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  // Photographs, not line art: smoothing is what we want here.
  context.imageSmoothingQuality = 'high';
  context.drawImage(image, 0, 0, width, height);

  if (typeof canvas.toBlob !== 'function') {
    throw new Error('ENCODE_UNSUPPORTED');
  }
  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((result) => (result ? resolve(result) : reject(new Error('ENCODE_FAILED'))), type, quality);
  });

  // Read the type back rather than trusting the request: a browser that
  // ignores the argument returns PNG, and naming that file .webp would give
  // the server a name its bytes contradict.
  const actualType = blob.type || type;
  const format = actualType === 'image/webp' ? 'webp' : actualType === 'image/png' ? 'png' : 'jpeg';
  const baseName = (file.name || 'photo').replace(/\.[^.]+$/, '');

  return {
    file: new File([blob], `${baseName}.${format}`, { type: actualType }),
    format,
    originalBytes: file.size,
    reencoded: true
  };
}
